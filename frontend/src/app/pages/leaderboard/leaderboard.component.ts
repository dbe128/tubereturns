import { Component, inject, signal, OnDestroy, OnInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, interval, of, Subject, Subscription } from 'rxjs';
import { catchError, debounceTime, switchMap } from 'rxjs/operators';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';
import { BackendRecoveryService } from '../../services/backend-recovery.service';
import { SpyChartComponent } from '../../components/spy-chart/spy-chart.component';
import type { Channel, ChannelStats, ChannelSearchResult, PipelineStepStatus } from '../../api/types';

interface ChannelRow extends Channel {
  stats: ChannelStats | null;
}

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SpyChartComponent],
  template: `
    <div class="max-w-screen-2xl mx-auto px-6 py-10">
      <div class="mb-8">
        <p class="text-gray-500 text-sm">Finance YouTubers ranked by historical stock pick performance</p>
      </div>

      @if (loading()) {
        <div class="flex justify-center py-20">
          <div class="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent"></div>
        </div>
      }

      @if (error()) {
        <div class="bg-danger-50 border border-danger-500 text-danger-500 rounded-xl p-4 text-sm">
          {{ error() }}
        </div>
      }

      @if (!loading() && !error()) {
        @if (auth.isAdmin && !showAddForm()) {
          <div class="mb-4 flex justify-end">
            <button
              (click)="openAddForm()"
              class="px-4 py-2 bg-gray-800 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 transition-colors"
            >+ Add Channel</button>
          </div>
        }

        @if (auth.isAdmin && showAddForm()) {
          <div class="bg-white border border-gray-200 rounded-xl shadow-sm p-4 w-full mb-4">
            <div class="flex items-center justify-between mb-3">
              <h3 class="text-sm font-semibold text-gray-700">Add stock picking channel</h3>
              <button (click)="cancelAddChannel()" class="text-gray-400 hover:text-gray-600 transition-colors text-lg leading-none">&times;</button>
            </div>
            <input
              [(ngModel)]="searchQuery"
              (input)="onSearchInput()"
              #searchInput
              placeholder="Search YouTube channels…"
              class="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
            @if (searching()) {
              <div class="flex justify-center py-6">
                <div class="animate-spin rounded-full h-5 w-5 border-2 border-primary-500 border-t-transparent"></div>
              </div>
            } @else if (searchResults().length > 0) {
              <ul class="mt-2 divide-y divide-gray-100">
                @for (result of searchResults(); track result.handle) {
                  <li>
                    <button
                      (click)="selectChannel(result)"
                      [disabled]="addingChannelId() !== null"
                      class="w-full flex items-center gap-3 px-2 py-2.5 rounded-lg hover:bg-gray-50 transition-colors disabled:opacity-50 text-left"
                    >
                      @if (result.thumbnailUrl) {
                        <img [src]="result.thumbnailUrl" [alt]="result.channelName"
                          referrerpolicy="no-referrer"
                          (error)="$any($event.target).style.display='none'"
                          class="w-9 h-9 rounded-full object-cover flex-shrink-0 ring-2 ring-gray-100" />
                      } @else {
                        <div class="w-9 h-9 rounded-full bg-gray-100 flex-shrink-0"></div>
                      }
                      <div class="min-w-0 flex-1">
                        <p class="text-sm font-semibold text-gray-800 truncate">{{ result.channelName }}</p>
                        <p class="text-xs text-gray-400 truncate">{{ result.channelUrl }}</p>
                      </div>
                      @if (addingChannelId() === result.handle) {
                        <div class="animate-spin rounded-full h-4 w-4 border-2 border-primary-500 border-t-transparent flex-shrink-0"></div>
                      } @else {
                        <span class="text-xs text-primary-600 font-semibold flex-shrink-0">Add</span>
                      }
                    </button>
                  </li>
                }
              </ul>
            }
            @if (addError()) {
              <p class="text-xs text-danger-500 mt-2">{{ addError() }}</p>
            }
          </div>
        }

        <div class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table class="w-full">
            <thead>
              <tr class="border-b border-gray-200 text-left text-xs font-semibold text-gray-400 uppercase tracking-wider">
                <th class="px-6 py-4">#</th>
                <th class="px-6 py-4">Channel</th>
                <th class="px-6 py-4 text-right">Videos</th>
                <th class="px-6 py-4 text-right">Processed</th>
                <th class="px-6 py-4">
                  <span class="text-primary-600">▲</span> Buy Picks
                </th>
                <th class="px-6 py-4">
                  <span class="text-danger-500">▼</span> Sell Picks
                </th>
                <th class="px-6 py-4"></th>
              </tr>
            </thead>
            <tbody>
              @if (rows().length === 0) {
                <tr>
                  <td colspan="6" class="px-6 py-16 text-center text-gray-400 text-sm">
                    No channels yet. Add a channel to get started.
                  </td>
                </tr>
              } @else {
                @for (row of rows(); track row.handle; let i = $index) {
                  <tr class="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                    <td class="px-6 py-4 text-gray-300 font-mono text-sm">{{ i + 1 }}</td>
                    <td class="px-6 py-4">
                      <a
                        [routerLink]="['/channel', row.handle]"
                        class="flex items-center gap-3 group"
                      >
                        @if (row.hasThumbnail) {
                          <img
                            [src]="'/api/channels/' + row.handle + '/thumbnail'"
                            [alt]="row.channelName"
                            class="w-9 h-9 rounded-full object-cover flex-shrink-0 ring-2 ring-gray-100"
                          />
                        } @else {
                          <div class="w-9 h-9 rounded-full bg-gray-100 flex-shrink-0"></div>
                        }
                        <span class="font-semibold text-gray-900 group-hover:text-primary-600 transition-colors">
                          {{ row.channelName }}
                        </span>
                      </a>
                    </td>
                    <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {{ row.stats?.totalVideos ?? '—' }}
                    </td>
                    <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {{ row.stats?.processedVideos ?? '—' }}
                    </td>
                    <td class="px-6 py-4 text-sm font-mono text-primary-600 font-medium">
                      @if (row.stats && row.stats.buyPicks.length > 0) {
                        {{ row.stats.buyPicks.join(', ') }}
                      } @else {
                        <span class="text-gray-200 font-normal">—</span>
                      }
                    </td>
                    <td class="px-6 py-4 text-sm font-mono text-danger-500 font-medium">
                      @if (row.stats && row.stats.sellPicks.length > 0) {
                        {{ row.stats.sellPicks.join(', ') }}
                      } @else {
                        <span class="text-gray-200 font-normal">—</span>
                      }
                    </td>
                    <td class="px-6 py-4">
                      <button
                        (click)="deleteChannel(row.handle, row.channelName)"
                        class="text-gray-300 hover:text-danger-500 transition-colors"
                        title="Remove channel"
                      >
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                          <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                        </svg>
                      </button>
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      }

      @if (!error()) {
        <app-spy-chart (refresh)="load()" />

        @if (auth.isAdmin) {
        <div class="mt-10">
          <h2 class="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-4">Pipeline</h2>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            @for (step of pipelineStatus(); track step.step) {
              <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-5">
                <div class="flex items-center justify-between mb-4">
                  <h3 class="font-semibold text-gray-800 text-sm">{{ step.label }}</h3>
                  @if (step.running) {
                    <span class="flex items-center gap-1.5 text-xs text-primary-600 font-medium">
                      <span class="animate-spin inline-block h-3 w-3 border border-primary-500 border-t-transparent rounded-full"></span>
                      Running
                    </span>
                  } @else {
                    <span class="text-xs text-gray-300">Idle</span>
                  }
                </div>
                <dl class="space-y-2 text-xs mb-5">
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last started</dt>
                    <dd class="text-gray-700 font-mono">
                      {{ step.lastStartedAt ? (step.lastStartedAt | date:'HH:mm:ss, dd MMM') : '—' }}
                    </dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last finished</dt>
                    <dd class="text-gray-700 font-mono">
                      {{ step.lastFinishedAt ? (step.lastFinishedAt | date:'HH:mm:ss, dd MMM') : '—' }}
                    </dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Next run</dt>
                    <dd class="text-gray-700 font-mono">
                      {{ step.nextRunAt ? (step.nextRunAt | date:'HH:mm:ss, dd MMM') : '—' }}
                    </dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last processed</dt>
                    <dd class="font-mono" [class]="step.lastRunCount !== null && step.lastRunCount >= step.limit ? 'text-amber-600' : 'text-gray-700'">
                      {{ step.lastRunCount !== null ? step.lastRunCount + ' / ' + step.limit : '—' }}
                    </dd>
                  </div>
                </dl>
                <button
                  (click)="triggerStep(step.step)"
                  [disabled]="step.running || disabledSteps().has(step.step)"
                  class="w-full px-3 py-1.5 bg-gray-800 text-white rounded-lg text-xs font-semibold
                         hover:bg-gray-700 disabled:opacity-40 transition-colors"
                >
                  Run now
                </button>
              </div>
            }
          </div>
        </div>
        }
      }
    </div>
  `,
})
export class LeaderboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  private readonly recovery = inject(BackendRecoveryService);

  readonly rows = signal<ChannelRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly pipelineStatus = signal<PipelineStepStatus[]>([]);
  readonly disabledSteps = signal<Set<string>>(new Set());
  readonly showAddForm = signal(false);
  @ViewChild('searchInput') private searchInputRef?: ElementRef<HTMLInputElement>;
  readonly searchResults = signal<ChannelSearchResult[]>([]);
  readonly searching = signal(false);
  readonly addingChannelId = signal<string | null>(null);
  readonly addError = signal<string | null>(null);
  searchQuery = '';
  private readonly searchSubject = new Subject<string>();
  private searchSub?: Subscription;

  private statusPollSub?: Subscription;
  private fastPollSub?: Subscription;

  ngOnInit(): void {
    this.load();
    if (this.auth.isAdmin) {
      this.loadPipelineStatus();
      this.statusPollSub = interval(15000).subscribe(() => this.loadPipelineStatus());
    }
    this.searchSub = this.searchSubject.pipe(
      debounceTime(400),
      switchMap((q) => q.trim().length >= 2 ? this.api.searchChannels(q).pipe(catchError(() => of<ChannelSearchResult[]>([]))) : of<ChannelSearchResult[]>([])),
    ).subscribe((results) => {
      this.searchResults.set(results);
      this.searching.set(false);
    });
  }

  ngOnDestroy(): void {
    this.recovery.stopPolling();
    this.statusPollSub?.unsubscribe();
    this.fastPollSub?.unsubscribe();
    this.searchSub?.unsubscribe();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.recovery.stopPolling();

    this.api.getChannels().subscribe({
      next: (channels) => {
        if (channels.length === 0) {
          this.rows.set([]);
          this.loading.set(false);
          return;
        }
        const stats$ = channels.map((ch) =>
          this.api.getChannelStats(ch.handle).pipe(catchError(() => of(null))),
        );
        forkJoin(stats$).subscribe({
          next: (statsArray) => {
            this.rows.set(channels.map((ch, i) => ({ ...ch, stats: statsArray[i] })));
            this.loading.set(false);
          },
          error: (err: unknown) => {
            this.error.set(String(err));
            this.loading.set(false);
            this.recovery.startPolling(() => this.load());
          },
        });
      },
      error: (err: unknown) => {
        this.error.set(String(err));
        this.loading.set(false);
        this.recovery.startPolling(() => this.load());
      },
    });
  }

  loadPipelineStatus(): void {
    this.api.getPipelineStatus().subscribe({
      next: (status) => this.pipelineStatus.set(status),
      error: () => {},
    });
  }

  triggerStep(step: string): void {
    this.disabledSteps.update((s) => new Set([...s, step]));
    setTimeout(() => this.disabledSteps.update((s) => { const n = new Set(s); n.delete(step); return n; }), 5000);
    this.api.triggerPipelineStep(step).subscribe({
      next: () => this.pollUntilDone(step),
      error: () => {},
    });
  }

  openAddForm(): void {
    this.showAddForm.set(true);
    setTimeout(() => this.searchInputRef?.nativeElement.focus(), 0);
  }

  onSearchInput(): void {
    const q = this.searchQuery.trim();
    if (q.length >= 2) {
      this.searching.set(true);
    } else {
      this.searchResults.set([]);
    }
    this.searchSubject.next(q);
  }

  selectChannel(result: ChannelSearchResult): void {
    this.addingChannelId.set(result.handle);
    this.addError.set(null);
    this.api.addChannel(result.handle, result.channelName, result.channelUrl, result.thumbnailUrl ?? '', result.description ?? '').subscribe({
      next: () => {
        this.addingChannelId.set(null);
        this.cancelAddChannel();
        this.load();
      },
      error: (err: unknown) => {
        this.addingChannelId.set(null);
        this.addError.set(String(err));
      },
    });
  }

  cancelAddChannel(): void {
    this.showAddForm.set(false);
    this.searchQuery = '';
    this.searchResults.set([]);
    this.searching.set(false);
    this.addError.set(null);
  }

  deleteChannel(channelId: string, channelName: string): void {
    if (!confirm(`Remove "${channelName}" from TubeReturns? This cannot be undone from the UI.`)) {
      return;
    }
    this.api.deleteChannel(channelId).subscribe({
      next: () => this.load(),
      error: () => {},
    });
  }

  private pollUntilDone(step: string): void {
    this.fastPollSub?.unsubscribe();
    let seenRunning = false;
    let polls = 0;
    const MAX_POLLS = 60;
    this.fastPollSub = interval(2000)
      .pipe(switchMap(() => this.api.getPipelineStatus()))
      .subscribe({
        next: (statuses) => {
          polls++;
          this.pipelineStatus.set(statuses);
          const current = statuses.find((s) => s.step === step);
          if (current?.running) {
            seenRunning = true;
          } else if (seenRunning || polls >= MAX_POLLS) {
            this.fastPollSub?.unsubscribe();
          }
        },
      });
  }
}
