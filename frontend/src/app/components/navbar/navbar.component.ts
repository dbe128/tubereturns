import { Component, inject, OnInit, OnDestroy, signal, computed, effect, untracked, HostListener } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, Subscription, of } from 'rxjs';
import { debounceTime, switchMap, catchError } from 'rxjs/operators';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../api/api.service';
import { ChannelStoreService } from '../../services/channel-store.service';
import type { ChannelSearchResult } from '../../api/types';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, FormsModule],
  template: `
    @if (toasts().length > 0) {
      <div class="fixed top-4 right-4 z-50 flex flex-col gap-2 max-w-sm">
        @for (t of toasts(); track t.id) {
          <div class="flex items-start gap-3 px-4 py-3 rounded-xl shadow-lg text-sm font-medium text-white"
               [class]="t.type === 'success' ? 'bg-green-600' : t.type === 'info' ? 'bg-blue-600' : 'bg-red-600'">
            <span class="flex-1">{{ t.message }}</span>
            <button (click)="dismissToast(t.id)" class="flex-shrink-0 opacity-70 hover:opacity-100 transition-opacity leading-none">&times;</button>
          </div>
        }
      </div>
    }

    <header class="bg-gray-100 border-b border-gray-200">
      <div class="px-6 h-[3.33rem] flex items-center justify-between">
        <div class="flex items-center gap-4">
          <a routerLink="/">
            <img src="logo.webp" alt="TubeReturns" class="h-36 rounded" />
          </a>

          <div class="relative w-[26rem]">
            <div class="flex items-center border border-gray-300 rounded-lg px-3 py-2 gap-2.5 bg-white shadow-sm focus-within:ring-2 focus-within:ring-green-500 focus-within:border-green-400 transition-shadow">
              @if (searching()) {
                <div class="w-4 h-4 rounded-full border-2 border-green-500 border-t-transparent animate-spin flex-shrink-0"></div>
              } @else {
                <svg class="w-4 h-4 text-gray-900 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z" />
                </svg>
              }
              <input
                [ngModel]="searchQuery()"
                (ngModelChange)="onSearchChange($event)"
                (blur)="hideSearch()"
                placeholder="Search stock-picking channels…"
                class="w-full text-sm focus:outline-none bg-transparent text-gray-900 placeholder-gray-500"
              />
            </div>
            @if (ytResults().length > 0) {
              <ul class="absolute z-20 mt-1 w-full bg-white border border-gray-200 rounded-lg shadow-lg overflow-hidden">
                @for (result of ytResults(); track result.handle) {
                  @let inDb = isInDb(result.handle);
                  <li>
                    <button
                      (mousedown)="handleSelect(result)"
                      [disabled]="addingHandle() === result.handle"
                      class="w-full flex items-center gap-3 px-3 py-2.5 hover:bg-gray-50 transition-colors text-left disabled:opacity-60"
                    >
                      @if (result.thumbnailUrl) {
                        <img [src]="result.thumbnailUrl" [alt]="result.channelName"
                             referrerpolicy="no-referrer"
                             (error)="hideImgOnError($event)"
                             class="w-8 h-8 rounded-full object-cover flex-shrink-0 ring-1 ring-gray-200" />
                      } @else {
                        <div class="w-8 h-8 rounded-full bg-gray-200 flex-shrink-0"></div>
                      }
                      <div class="min-w-0 flex-1">
                        <p class="text-sm font-semibold text-gray-900 truncate">{{ result.channelName }}</p>
                        <p class="text-xs text-gray-500">&#64;{{ result.handle }}</p>
                      </div>
                      @if (addingHandle() === result.handle) {
                        <div class="w-4 h-4 rounded-full border-2 border-primary-500 border-t-transparent animate-spin flex-shrink-0"></div>
                      } @else if (inDb) {
                        <span class="text-xs text-green-600 font-semibold flex-shrink-0">View</span>
                      } @else {
                        <span class="text-xs text-primary-600 font-semibold flex-shrink-0">Add</span>
                      }
                    </button>
                  </li>
                }
              </ul>
            }
          </div>
        </div>

        <div class="flex items-center gap-3">
          @if (auth.isAdmin) {
            <a routerLink="/admin"
              class="px-3 py-1.5 text-sm text-gray-600 hover:text-gray-900 font-semibold transition-colors">
              Admin
            </a>
          }
          @if (auth.isAuthenticated) {
            <div class="relative">
              <button (click)="toggleUserMenu()" class="focus:outline-none">
                @if (auth.user()?.profilePictureUrl) {
                  <img [src]="auth.user()!.profilePictureUrl!" [alt]="auth.user()!.firstName"
                       referrerpolicy="no-referrer"
                       class="w-8 h-8 rounded-full object-cover ring-1 ring-gray-300 flex-shrink-0 cursor-pointer hover:ring-2 hover:ring-green-500 transition-shadow" />
                } @else {
                  <div class="w-8 h-8 rounded-full bg-green-600 flex items-center justify-center text-white text-xs font-bold flex-shrink-0 cursor-pointer hover:bg-green-700 transition-colors">
                    {{ userInitials() }}
                  </div>
                }
              </button>
              @if (userMenuOpen()) {
                <div class="absolute right-0 mt-2 w-40 bg-white border border-gray-200 rounded-xl shadow-lg z-50 py-1">
                  <button (click)="logout()"
                    class="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors">
                    Sign out
                  </button>
                </div>
              }
            </div>
          } @else {
            <a routerLink="/login"
              class="px-3 py-1.5 text-sm font-semibold text-gray-700 border border-gray-300 rounded-lg hover:border-gray-400 hover:text-gray-900 transition-colors">
              Sign in
            </a>
            <a routerLink="/signup"
              class="px-3 py-1.5 bg-green-600 text-white rounded-lg text-sm font-semibold hover:bg-green-700 transition-colors">
              Sign up
            </a>
          }
        </div>
      </div>
    </header>
  `,
})
export class NavbarComponent implements OnInit, OnDestroy {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  private readonly channelStore = inject(ChannelStoreService);

  readonly searchQuery = signal('');
  readonly ytResults = signal<ChannelSearchResult[]>([]);
  readonly searching = signal(false);
  readonly addingHandle = signal<string | null>(null);
  readonly toasts = signal<{ id: number; message: string; type: 'success' | 'error' | 'info' }[]>([]);
  readonly userMenuOpen = signal(false);
  private readonly pendingAdd = signal<ChannelSearchResult | null>(null);
  private toastTimers = new Map<number, ReturnType<typeof setTimeout>>();
  private toastCounter = 0;

  private readonly searchSubject = new Subject<string>();
  private searchSub?: Subscription;

  constructor() {
    effect(() => {
      const user = this.auth.user();
      const pending = this.pendingAdd();
      if (!user || !pending) { return; }
      untracked(() => {
        this.pendingAdd.set(null);
        localStorage.removeItem('pendingAddChannel');
        this.addingHandle.set(pending.handle);
        if (user.role === 'ADMIN') {
          this.proceedWithAdd(pending, 'ADMIN');
        } else {
          this.runEligibilityAndAdd(pending);
        }
      });
    });
  }

  readonly dbHandles = computed(() => new Set(this.channelStore.channels().map(c => c.handle)));

  readonly userInitials = computed(() => {
    const u = this.auth.user();
    if (!u) { return ''; }
    const first = u.firstName?.[0] ?? '';
    const last = u.lastName?.[0] ?? '';
    return (first + last).toUpperCase();
  });

  ngOnInit(): void {
    const stored = localStorage.getItem('pendingAddChannel');
    if (stored) {
      try { this.pendingAdd.set(JSON.parse(stored) as ChannelSearchResult); }
      catch { localStorage.removeItem('pendingAddChannel'); }
    }
    this.searchSub = this.searchSubject.pipe(
      debounceTime(400),
      switchMap((q) => {
        if (q.trim().length < 2) { return of<ChannelSearchResult[]>([]); }
        const local = this.searchLocally(q);
        if (local.length > 0) { return of(local); }
        return this.api.searchChannels(q, true).pipe(catchError(() => of<ChannelSearchResult[]>([])));
      }),
    ).subscribe((results) => {
      this.ytResults.set(results);
      this.searching.set(false);
    });
  }

  ngOnDestroy(): void {
    this.searchSub?.unsubscribe();
    this.toastTimers.forEach(t => clearTimeout(t));
  }

  private searchLocally(q: string): ChannelSearchResult[] {
    const lower = q.toLowerCase();
    return this.channelStore.channels()
      .filter(c => c.handle.toLowerCase().includes(lower) || c.channelName.toLowerCase().includes(lower))
      .slice(0, 5)
      .map(c => ({
        handle: c.handle,
        channelName: c.channelName,
        channelUrl: `https://www.youtube.com/@${c.handle}`,
        thumbnailUrl: c.hasThumbnail ? `/api/channels/${c.handle}/thumbnail` : null,
        description: c.description,
        subscriberCount: c.subscriberCount,
        videoCount: c.totalVideos,
        channelCreatedAt: null,
      }));
  }

  isInDb(handle: string): boolean {
    return this.dbHandles().has(handle);
  }

  onSearchChange(q: string): void {
    this.searchQuery.set(q);
    if (q.trim().length >= 2) {
      this.searching.set(true);
    } else {
      this.ytResults.set([]);
      this.searching.set(false);
    }
    this.searchSubject.next(q);
  }

  handleSelect(result: ChannelSearchResult): void {
    if (this.isInDb(result.handle)) {
      this.searchQuery.set('');
      this.ytResults.set([]);
      this.router.navigate(['/channel', result.handle]);
      return;
    }
    if (!this.auth.isAuthenticated) {
      this.pendingAdd.set(result);
      localStorage.setItem('pendingAddChannel', JSON.stringify(result));
      this.searchQuery.set('');
      this.ytResults.set([]);
      this.router.navigate(['/login']);
      return;
    }
    this.addingHandle.set(result.handle);
    if (this.auth.isAdmin) {
      this.proceedWithAdd(result, 'ADMIN');
      return;
    }
    this.runEligibilityAndAdd(result);
  }

  private runEligibilityAndAdd(result: ChannelSearchResult): void {
    const eligibilityToastId = this.showToast(`Checking eligibility of the suggested "${result.channelName}" channel as a stock-picking channel. This might take a while, please wait…`, 'info');
    this.api.assessChannelRelevance(result.handle, result.channelName).subscribe({
      next: (relevance) => {
        this.dismissToast(eligibilityToastId);
        if (!relevance.passed) {
          this.addingHandle.set(null);
          this.showToast(`${result.channelName} does not appear to be a stock-picking channel (score: ${relevance.score}/10). Only channels strictly focused on individual stock picks are accepted — crypto, ETF, bond, general finance, and news channels do not qualify.`, 'error');
          return;
        }
        this.proceedWithAdd(result);
      },
      error: () => {
        this.dismissToast(eligibilityToastId);
        this.addingHandle.set(null);
        this.showToast(`Could not verify eligibility for ${result.channelName}. Please try again.`, 'error');
      },
    });
  }

  private proceedWithAdd(result: ChannelSearchResult, approvalSource: string = 'AUTO'): void {
    this.api.addChannel(result.handle, result.channelName, result.channelUrl, result.thumbnailUrl ?? '', result.description ?? '', result.subscriberCount, true, approvalSource).subscribe({
      next: () => {
        this.addingHandle.set(null);
        this.searchQuery.set('');
        this.ytResults.set([]);
        this.channelStore.load();
        this.showToast(`${result.channelName} has been added and is now being tracked.`, 'success');
        this.router.navigate(['/channel', result.handle]);
      },
      error: () => {
        this.addingHandle.set(null);
        this.showToast(`Failed to add ${result.channelName}.`, 'error');
      },
    });
  }

  hideSearch(): void {
    setTimeout(() => {
      this.searchQuery.set('');
      this.ytResults.set([]);
    }, 150);
  }

  private showToast(message: string, type: 'success' | 'error' | 'info'): number {
    const id = ++this.toastCounter;
    this.toasts.update(ts => [...ts, { id, message, type }]);
    if (type !== 'info') {
      this.toastTimers.set(id, setTimeout(() => this.dismissToast(id), 6000));
    }
    return id;
  }

  protected dismissToast(id: number): void {
    clearTimeout(this.toastTimers.get(id));
    this.toastTimers.delete(id);
    this.toasts.update(ts => ts.filter(t => t.id !== id));
  }

  hideImgOnError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }

  toggleUserMenu(): void {
    this.userMenuOpen.update(v => !v);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (!target.closest('.relative')) {
      this.userMenuOpen.set(false);
    }
  }

  logout(): void {
    this.userMenuOpen.set(false);
    this.auth.logout();
    this.router.navigate(['/']);
  }
}
