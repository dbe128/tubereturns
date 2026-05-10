import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';
import { BackendRecoveryService } from '../../services/backend-recovery.service';
import type { Channel, ChannelStats, VideoSummary } from '../../api/types';

type SortKey = 'index' | 'publishedAt' | 'viewCount' | 'transcriptStatus' | 'processingStatus';
type SortDir = 'asc' | 'desc';

const TRANSCRIPT_ORDER: Record<VideoSummary['transcriptStatus'], number> = {
  DOWNLOADED: 0, NO_TRANSCRIPT: 1, PENDING: 2, DOWNLOADING: 3, FAILED: 4,
};

const PROCESSING_ORDER: Record<VideoSummary['processingStatus'], number> = {
  COMPLETED: 0, PROCESSING: 1, PENDING: 2, FAILED: 3,
};

const TRANSCRIPT_LABELS: Record<VideoSummary['transcriptStatus'], string> = {
  DOWNLOADING: 'Downloading',
  DOWNLOADED: 'Downloaded',
  NO_TRANSCRIPT: 'No transcript',
  FAILED: 'Failed',
  PENDING: 'Pending',
};

const PROCESSING_LABELS: Record<VideoSummary['processingStatus'], string> = {
  COMPLETED: 'Extracted',
  PROCESSING: 'Processing',
  FAILED: 'Failed',
  PENDING: 'Pending',
};

const TRANSCRIPT_STYLES: Record<VideoSummary['transcriptStatus'], string> = {
  DOWNLOADING: 'bg-blue-50 text-blue-600 animate-pulse',
  DOWNLOADED: 'bg-primary-50 text-primary-700',
  NO_TRANSCRIPT: 'bg-yellow-50 text-yellow-700',
  FAILED: 'bg-danger-50 text-danger-500',
  PENDING: 'bg-gray-100 text-gray-400',
};

const PROCESSING_STYLES: Record<VideoSummary['processingStatus'], string> = {
  COMPLETED: 'bg-primary-50 text-primary-700',
  PROCESSING: 'bg-blue-50 text-blue-600',
  FAILED: 'bg-danger-50 text-danger-500',
  PENDING: 'bg-gray-100 text-gray-400',
};

interface IndexedVideo {
  v: VideoSummary;
  originalIndex: number;
}

@Component({
  selector: 'app-channel-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  template: `
    @if (loading()) {
      <div class="flex justify-center py-20">
        <div class="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent"></div>
      </div>
    } @else if (error() || !channel()) {
      <div class="max-w-5xl mx-auto px-4 py-10">
        <div class="bg-danger-50 border border-danger-500 text-danger-500 rounded-xl p-4 text-sm">
          {{ error() ?? 'Channel not found' }}
        </div>
        <a routerLink="/" class="mt-4 inline-block text-primary-600 hover:text-primary-700 text-sm font-medium">
          ← Back to leaderboard
        </a>
      </div>
    } @else {
      <div class="max-w-screen-2xl mx-auto px-6 py-10">
        <a routerLink="/" class="text-primary-600 hover:text-primary-700 text-sm font-medium mb-6 inline-block">
          ← Leaderboard
        </a>

        <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mb-4">
          <div class="flex items-center gap-4">
            @if (channel()!.hasThumbnail) {
              <img
                [src]="'/api/channels/' + channel()!.handle + '/thumbnail'"
                [alt]="channel()!.channelName"
                class="w-14 h-14 rounded-full ring-2 ring-gray-100 flex-shrink-0"
              />
            }
            <div class="flex-1 min-w-0">
              <h1 class="text-xl font-bold text-gray-900">{{ channel()!.channelName }}</h1>
              <a
                [href]="'https://www.youtube.com/@' + channel()!.handle"
                target="_blank"
                rel="noreferrer"
                class="text-primary-600 hover:text-primary-700 text-xs mt-0.5 inline-block"
              >youtube.com/@{{ channel()!.handle }} ↗</a>
            </div>
            <div class="flex items-center gap-6 flex-shrink-0">
              <div class="flex gap-8 text-sm text-gray-400">
                <div class="text-center">
                  <div class="text-2xl font-bold text-gray-800">{{ stats()?.totalVideos ?? '—' }}</div>
                  <div class="text-xs uppercase tracking-wide">Videos</div>
                </div>
                <div class="text-center">
                  <div class="text-2xl font-bold text-primary-600">{{ stats()?.processedVideos ?? '—' }}</div>
                  <div class="text-xs uppercase tracking-wide">Processed</div>
                </div>
              </div>
              @if (auth.isAdmin) {
                <button
                  (click)="refresh()"
                  [disabled]="loading()"
                  title="Refresh"
                  class="p-2 rounded-lg text-gray-400 hover:text-gray-700 hover:bg-gray-100 disabled:opacity-40 transition-colors"
                  [class.animate-spin]="loading()"
                >↺</button>
              }
            </div>
          </div>
        </div>

        <div class="bg-white border border-gray-200 rounded-xl shadow-sm px-5 py-4 mb-4">
          <div class="flex items-center justify-between mb-3">
            <span class="text-xs font-semibold text-gray-400 uppercase tracking-wider">Filters</span>
            @if (filterTranscript() || filterProcessing() || filterPick()) {
              <button
                (click)="clearFilters()"
                class="text-xs text-primary-600 hover:text-primary-800 font-medium"
              >Clear all</button>
            }
          </div>
          <div class="flex flex-wrap gap-4">
            @if (auth.isAdmin) {
            <div class="flex flex-col gap-1">
              <label class="text-xs text-gray-500">Transcript status</label>
              <select
                [ngModel]="filterTranscript()"
                (ngModelChange)="filterTranscript.set($event)"
                class="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-gray-50 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-300"
              >
                <option value="">All transcript statuses</option>
                <option value="DOWNLOADING">Downloading</option>
                <option value="DOWNLOADED">Downloaded</option>
                <option value="NO_TRANSCRIPT">No transcript</option>
                <option value="FAILED">Failed</option>
                <option value="PENDING">Pending</option>
              </select>
            </div>

            <div class="flex flex-col gap-1">
              <label class="text-xs text-gray-500">Pick extraction</label>
              <select
                [ngModel]="filterProcessing()"
                (ngModelChange)="filterProcessing.set($event)"
                class="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-gray-50 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-300"
              >
                <option value="">All pick statuses</option>
                <option value="COMPLETED">Extracted</option>
                <option value="PROCESSING">Processing</option>
                <option value="FAILED">Failed</option>
                <option value="PENDING">Pending</option>
              </select>
            </div>
            }

            <div class="flex flex-col gap-1">
              <label class="text-xs text-gray-500">Ticker</label>
              <select
                [ngModel]="filterPick()"
                (ngModelChange)="filterPick.set($event)"
                class="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-gray-50 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-300"
              >
                <option value="">All picks</option>
                @for (ticker of allTickers(); track ticker) {
                  <option [value]="ticker">{{ ticker }}</option>
                }
              </select>
            </div>

            <div class="flex items-end pb-1">
              <label class="flex items-center gap-2 text-sm text-gray-500 cursor-pointer select-none">
                <input
                  type="checkbox"
                  [ngModel]="showExcluded()"
                  (ngModelChange)="showExcluded.set($event)"
                  class="rounded border-gray-300 text-primary-600 focus:ring-primary-300"
                />
                Show excluded
              </label>
            </div>

            @if (filterTranscript() || filterProcessing() || filterPick()) {
              <div class="flex items-end">
                <span class="text-xs text-gray-400 pb-2">
                  {{ sorted().length }} of {{ videos().length }} videos
                </span>
              </div>
            }
          </div>
        </div>

        <div class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          <table class="w-full text-sm">
            <thead>
              <tr class="bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500 uppercase tracking-wider">
                <th
                  class="px-4 py-3 w-12 cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('index')"
                >#<span class="ml-1" [class.text-primary-500]="sortKey() === 'index'" [class.text-gray-300]="sortKey() !== 'index'">{{ sortKey() === 'index' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                <th class="px-4 py-3 w-44"></th>
                <th class="px-4 py-3 w-52">Video</th>
                <th
                  class="px-4 py-3 w-28 cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('publishedAt')"
                >Upload Date<span class="ml-1" [class.text-primary-500]="sortKey() === 'publishedAt'" [class.text-gray-300]="sortKey() !== 'publishedAt'">{{ sortKey() === 'publishedAt' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                <th
                  class="px-4 py-3 w-24 text-right whitespace-nowrap cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('viewCount')"
                >Views<span class="ml-1" [class.text-primary-500]="sortKey() === 'viewCount'" [class.text-gray-300]="sortKey() !== 'viewCount'">{{ sortKey() === 'viewCount' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                @if (auth.isAdmin) {
                <th
                  class="px-4 py-3 w-32 cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('transcriptStatus')"
                >Transcript<span class="ml-1" [class.text-primary-500]="sortKey() === 'transcriptStatus'" [class.text-gray-300]="sortKey() !== 'transcriptStatus'">{{ sortKey() === 'transcriptStatus' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                <th
                  class="px-4 py-3 w-28 cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('processingStatus')"
                >Picks<span class="ml-1" [class.text-primary-500]="sortKey() === 'processingStatus'" [class.text-gray-300]="sortKey() !== 'processingStatus'">{{ sortKey() === 'processingStatus' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                <th class="px-4 py-3 w-36 text-gray-500">Model</th>
                }
                <th class="px-4 py-3 text-primary-600 whitespace-nowrap">▲ Buy</th>
                <th class="px-4 py-3 text-danger-500 whitespace-nowrap">▼ Sell</th>
                @if (auth.isAdmin) {
                <th class="px-4 py-3 w-32 whitespace-nowrap">Excl. Reason</th>
                <th class="px-4 py-3 w-24">Actions</th>
                }
              </tr>
            </thead>
            <tbody>
              @if (sorted().length === 0) {
                <tr>
                  <td [attr.colspan]="auth.isAdmin ? 12 : 7" class="px-4 py-12 text-center text-gray-400">
                    No videos match the current filters.
                  </td>
                </tr>
              } @else {
                @for (item of sorted(); track item.v.videoId) {
                  <tr class="border-b border-gray-100" [class.hover:bg-gray-50]="!item.v.excluded" [class.bg-amber-50]="item.v.excluded" [class.hover:bg-amber-100]="item.v.excluded">
                    <td class="px-4 py-3 text-gray-400 font-mono">{{ item.originalIndex }}</td>
                    <td class="px-4 py-3">
                      <a
                        [href]="'https://www.youtube.com/watch?v=' + item.v.videoId"
                        target="_blank"
                        rel="noreferrer"
                      >
                        <img
                          [src]="'https://img.youtube.com/vi/' + item.v.videoId + '/mqdefault.jpg'"
                          alt=""
                          class="w-40 rounded object-cover aspect-video"
                        />
                      </a>
                    </td>
                    <td class="px-4 py-3 max-w-0">
                      <a
                        [href]="'https://www.youtube.com/watch?v=' + item.v.videoId"
                        target="_blank"
                        rel="noreferrer"
                        [title]="item.v.title"
                        class="text-gray-900 hover:text-primary-600 block truncate"
                      >{{ item.v.title }}</a>
                    </td>
                    <td class="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {{ item.v.publishedAt | date: 'shortDate' }}
                    </td>
                    <td class="px-4 py-3 text-gray-500 text-right font-mono text-xs whitespace-nowrap">
                      {{ item.v.viewCount !== null ? (item.v.viewCount | number) : '—' }}
                    </td>
                    @if (auth.isAdmin) {
                    <td class="px-4 py-3">
                      <span
                        class="inline-block px-2 py-0.5 rounded text-xs font-medium select-none"
                        [class.cursor-pointer]="item.v.transcriptStatus === 'DOWNLOADED'"
                        [class.cursor-default]="item.v.transcriptStatus !== 'DOWNLOADED'"
                        [ngClass]="transcriptStyle(item.v.transcriptStatus)"
                        (click)="openTranscript(item.v)"
                      >{{ transcriptLabel(item.v.transcriptStatus) }}</span>
                    </td>
                    <td class="px-4 py-3">
                      <span
                        class="inline-block px-2 py-0.5 rounded text-xs font-medium"
                        [ngClass]="processingStyle(item.v.processingStatus)"
                      >{{ processingLabel(item.v.processingStatus) }}</span>
                    </td>
                    <td class="px-4 py-3 text-gray-400 font-mono text-xs">
                      @if (item.v.extractionModel) {
                        {{ item.v.extractionModel.replace('openrouter/', '') }}
                      } @else {
                        <span class="text-gray-200">—</span>
                      }
                    </td>
                    }
                    <td class="px-4 py-3 font-mono font-medium text-sm" [class.text-primary-600]="!item.v.excluded" [class.text-gray-400]="item.v.excluded" [class.line-through]="item.v.excluded">
                      @if (item.v.buyPicks.length > 0) {
                        @for (ticker of item.v.buyPicks; track ticker; let last = $last) {
                          <span class="relative group/tk inline-block">{{ ticker }}
                            @if (tickerMap()[ticker]) {
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs font-normal text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tk:opacity-100 transition-opacity z-50">{{ tickerMap()[ticker] }}</span>
                            }
                          </span>@if (!last) {, }
                        }
                      } @else {
                        <span class="font-normal" [class.text-gray-200]="!item.v.excluded" [class.text-gray-300]="item.v.excluded">—</span>
                      }
                    </td>
                    <td class="px-4 py-3 font-mono font-medium text-sm" [class.text-danger-500]="!item.v.excluded" [class.text-gray-400]="item.v.excluded" [class.line-through]="item.v.excluded">
                      @if (item.v.sellPicks.length > 0) {
                        @for (ticker of item.v.sellPicks; track ticker; let last = $last) {
                          <span class="relative group/tk inline-block">{{ ticker }}
                            @if (tickerMap()[ticker]) {
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs font-normal text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tk:opacity-100 transition-opacity z-50">{{ tickerMap()[ticker] }}</span>
                            }
                          </span>@if (!last) {, }
                        }
                      } @else {
                        <span class="font-normal" [class.text-gray-200]="!item.v.excluded" [class.text-gray-300]="item.v.excluded">—</span>
                      }
                    </td>
                    @if (auth.isAdmin) {
                    <td class="px-4 py-3">
                      @if (item.v.exclusionReason) {
                        <span class="text-xs text-amber-600 font-medium whitespace-nowrap">{{ item.v.exclusionReason }}</span>
                      } @else {
                        <span class="text-gray-200">—</span>
                      }
                    </td>
                    <td class="px-4 py-3">
                      <div class="flex items-center gap-2">
                        @if (!item.v.excluded && item.v.transcriptStatus !== 'DOWNLOADING' && !isMockChannel()) {
                          <button
                            (click)="handleRedownloadTranscript(item.v)"
                            [disabled]="redownloading() === item.v.videoId"
                            [title]="item.v.transcriptStatus === 'PENDING' ? 'Download transcript' : 'Re-download transcript'"
                            class="p-1 rounded transition-colors text-gray-400 hover:text-blue-600 hover:bg-blue-50"
                            [class.opacity-40]="redownloading() === item.v.videoId"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                              <path d="M10.75 2.75a.75.75 0 0 0-1.5 0v8.614L6.295 8.235a.75.75 0 1 0-1.09 1.03l4.25 4.5a.75.75 0 0 0 1.09 0l4.25-4.5a.75.75 0 0 0-1.09-1.03l-2.955 3.129V2.75Z" />
                              <path d="M3.5 12.75a.75.75 0 0 0-1.5 0v2.5A2.75 2.75 0 0 0 4.75 18h10.5A2.75 2.75 0 0 0 18 15.25v-2.5a.75.75 0 0 0-1.5 0v2.5c0 .69-.56 1.25-1.25 1.25H4.75c-.69 0-1.25-.56-1.25-1.25v-2.5Z" />
                            </svg>
                          </button>
                          @if (item.v.transcriptStatus === 'DOWNLOADED' && item.v.processingStatus !== 'PROCESSING') {
                            <button
                              (click)="handleReextract(item.v.videoId)"
                              [disabled]="reextracting() === item.v.videoId"
                              title="Re-extract picks"
                              class="text-lg leading-none transition-colors"
                              [class.text-primary-500]="reextracting() === item.v.videoId"
                              [class.animate-spin]="reextracting() === item.v.videoId"
                              [class.text-gray-400]="reextracting() !== item.v.videoId"
                              [class.hover:text-primary-600]="reextracting() !== item.v.videoId"
                            >↺</button>
                          }
                        }
                        <button
                          (click)="handleToggleExclusion(item.v)"
                          [disabled]="togglingExclusion() === item.v.videoId"
                          [title]="item.v.excluded ? 'Include in returns' : 'Exclude from returns'"
                          class="p-1 rounded transition-colors"
                          [class.text-yellow-600]="!item.v.excluded"
                          [class.hover:bg-yellow-100]="!item.v.excluded"
                          [class.text-gray-400]="item.v.excluded"
                          [class.hover:bg-gray-100]="item.v.excluded"
                          [class.opacity-40]="togglingExclusion() === item.v.videoId"
                        >
                          @if (item.v.excluded) {
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                              <path d="M10 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5Z" />
                              <path fill-rule="evenodd" d="M.664 10.59a1.651 1.651 0 0 1 0-1.186A10.004 10.004 0 0 1 10 3c4.257 0 7.893 2.66 9.336 6.41.147.381.146.804 0 1.186A10.004 10.004 0 0 1 10 17c-4.257 0-7.893-2.66-9.336-6.41Z" clip-rule="evenodd" />
                            </svg>
                          } @else {
                            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
                              <path fill-rule="evenodd" d="M3.28 2.22a.75.75 0 0 0-1.06 1.06l14.5 14.5a.75.75 0 1 0 1.06-1.06l-1.745-1.745a10.029 10.029 0 0 0 3.3-4.38 1.651 1.651 0 0 0 0-1.185A10.004 10.004 0 0 0 9.999 3a9.956 9.956 0 0 0-4.744 1.194L3.28 2.22ZM7.752 6.69l1.092 1.092a2.5 2.5 0 0 1 3.374 3.373l1.091 1.092a4 4 0 0 0-5.557-5.557Z" clip-rule="evenodd" />
                              <path d="m10.748 13.93 2.523 2.523a9.987 9.987 0 0 1-3.27.547c-4.258 0-7.894-2.66-9.337-6.41a1.651 1.651 0 0 1 0-1.186A10.007 10.007 0 0 1 2.839 6.02L6.07 9.252a4 4 0 0 0 4.678 4.678Z" />
                            </svg>
                          }
                        </button>
                      </div>
                    </td>
                    }
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      </div>

      @if (transcriptPopup()) {
        <div
          class="fixed inset-0 z-40"
          (click)="closeTranscript()"
        ></div>
        <div
          class="fixed z-50 overflow-y-auto bg-white border border-gray-200 rounded-xl shadow-2xl p-5 text-xs text-gray-700 whitespace-pre-wrap leading-relaxed"
          [style.top.px]="transcriptPopupPos().top"
          [style.left.px]="transcriptPopupPos().left"
          [style.width.px]="transcriptPopupPos().width"
          [style.max-height.px]="transcriptPopupPos().maxHeight"
          (click)="$event.stopPropagation()"
        >
          <div class="flex items-center justify-between mb-3">
            <span class="text-xs font-semibold text-gray-400 uppercase tracking-wider">Transcript</span>
            <button (click)="closeTranscript()" class="text-gray-400 hover:text-gray-600 text-base leading-none">✕</button>
          </div>
          {{ transcriptPopup() }}
        </div>
      }
    }
  `,
})
export class ChannelDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly recovery = inject(BackendRecoveryService);

  readonly tickerMap = signal<Record<string, string>>({});
  readonly channel = signal<Channel | null>(null);
  readonly stats = signal<ChannelStats | null>(null);
  readonly videos = signal<VideoSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly reextracting = signal<string | null>(null);
  readonly togglingExclusion = signal<string | null>(null);
  readonly redownloading = signal<string | null>(null);
  readonly isMockChannel = computed(() => this.channel()?.handle?.startsWith('mock-') ?? false);

  readonly sortKey = signal<SortKey>('publishedAt');
  readonly sortDir = signal<SortDir>('desc');
  readonly filterTranscript = signal<VideoSummary['transcriptStatus'] | ''>('');
  readonly filterProcessing = signal<VideoSummary['processingStatus'] | ''>('');
  readonly filterPick = signal('');
  readonly showExcluded = signal(false);

  readonly transcriptPopup = signal<string | null>(null);
  readonly transcriptPopupPos = signal({ top: 0, left: 0, width: 520, maxHeight: 600 });

  readonly allTickers = computed(() => {
    const tickers = new Set<string>();
    this.videos().forEach((v) => {
      v.buyPicks.forEach((t) => tickers.add(t));
      v.sellPicks.forEach((t) => tickers.add(t));
    });
    return Array.from(tickers).sort();
  });

  readonly filtered = computed(() =>
    this.videos().filter((v) => {
      if (!this.showExcluded() && v.excluded) return false;
      if (this.filterTranscript() && v.transcriptStatus !== this.filterTranscript()) return false;
      if (this.filterProcessing() && v.processingStatus !== this.filterProcessing()) return false;
      if (this.filterPick() && !v.buyPicks.includes(this.filterPick()) && !v.sellPicks.includes(this.filterPick())) return false;
      return true;
    }),
  );

  readonly sorted = computed((): IndexedVideo[] => {
    const vids = this.videos();
    const withIndex: IndexedVideo[] = this.filtered().map((v) => ({
      v,
      originalIndex: vids.indexOf(v) + 1,
    }));
    const key = this.sortKey();
    const dir = this.sortDir();
    withIndex.sort((a, b) => {
      let cmp = 0;
      if (key === 'index') {
        cmp = a.originalIndex - b.originalIndex;
      } else if (key === 'publishedAt') {
        cmp = new Date(a.v.publishedAt).getTime() - new Date(b.v.publishedAt).getTime();
      } else if (key === 'viewCount') {
        cmp = (a.v.viewCount ?? -1) - (b.v.viewCount ?? -1);
      } else if (key === 'transcriptStatus') {
        cmp = TRANSCRIPT_ORDER[a.v.transcriptStatus] - TRANSCRIPT_ORDER[b.v.transcriptStatus];
      } else if (key === 'processingStatus') {
        cmp = PROCESSING_ORDER[a.v.processingStatus] - PROCESSING_ORDER[b.v.processingStatus];
      }
      return dir === 'asc' ? cmp : -cmp;
    });
    return withIndex;
  });

  ngOnInit(): void {
    this.api.getTickers().subscribe({ next: (m) => this.tickerMap.set(m), error: () => {} });
    const channelId = this.route.snapshot.paramMap.get('channelId');
    if (channelId) {
      this.loadData(channelId);
    }
  }

  ngOnDestroy(): void {
    this.recovery.stopPolling();
  }

  loadData(id: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.recovery.stopPolling();

    forkJoin({
      channel: this.api.getChannel(id),
      stats: this.api.getChannelStats(id).pipe(catchError(() => of(null))),
      videos: this.api.getVideosForChannel(id),
    }).subscribe({
      next: ({ channel, stats, videos }) => {
        this.channel.set(channel);
        this.stats.set(stats);
        this.videos.set(videos);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(String(err));
        this.loading.set(false);
        this.recovery.startPolling(() => {
          const cid = this.route.snapshot.paramMap.get('channelId');
          if (cid) this.loadData(cid);
        });
      },
    });
  }

  refresh(): void {
    const cid = this.route.snapshot.paramMap.get('channelId');
    if (cid) this.loadData(cid);
  }

  toggleSort(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortKey.set(key);
      this.sortDir.set('asc');
    }
  }

  clearFilters(): void {
    this.filterTranscript.set('');
    this.filterProcessing.set('');
    this.filterPick.set('');
    this.showExcluded.set(false);
  }

  handleToggleExclusion(video: VideoSummary): void {
    const newExcluded = !video.excluded;
    this.togglingExclusion.set(video.videoId);
    this.api.setVideoExcluded(video.videoId, newExcluded).subscribe({
      next: () => {
        this.videos.update(list =>
          list.map(v => v.videoId === video.videoId
            ? { ...v, excluded: newExcluded, exclusionReason: newExcluded ? 'Manual' : null }
            : v)
        );
        this.togglingExclusion.set(null);
      },
      error: () => {
        this.togglingExclusion.set(null);
      },
    });
  }

  handleRedownloadTranscript(video: VideoSummary): void {
    this.redownloading.set(video.videoId);
    this.api.redownloadTranscript(video.videoId).subscribe({
      next: () => {
        this.redownloading.set(null);
        this.reloadVideos();
      },
      error: () => {
        this.redownloading.set(null);
      },
    });
  }

  private reloadVideos(): void {
    const handle = this.route.snapshot.paramMap.get('channelId');
    if (!handle) return;
    this.api.getVideosForChannel(handle).subscribe({
      next: (videos) => this.videos.set(videos),
      error: () => {},
    });
  }

  handleReextract(videoId: string): void {
    this.reextracting.set(videoId);
    this.api.reextractVideo(videoId).subscribe({
      next: () => {
        this.reextracting.set(null);
        this.reloadVideos();
      },
      error: () => {
        this.reextracting.set(null);
      },
    });
  }

  openTranscript(v: VideoSummary): void {
    if (!v.transcriptText) return;
    const popupWidth = 520;
    const popupHeight = Math.min(window.innerHeight * 0.75, 600);
    const margin = 12;
    let left = margin;
    let top = margin;
    if (popupWidth + 2 * margin < window.innerWidth) {
      left = Math.max(margin, Math.min(window.innerWidth - popupWidth - margin, window.innerWidth / 2 - popupWidth / 2));
    }
    top = Math.max(margin, window.innerHeight / 2 - popupHeight / 2);
    this.transcriptPopupPos.set({ top, left, width: popupWidth, maxHeight: popupHeight });
    this.transcriptPopup.set(v.transcriptText);
  }

  closeTranscript(): void {
    this.transcriptPopup.set(null);
  }

  transcriptLabel(status: VideoSummary['transcriptStatus']): string {
    return TRANSCRIPT_LABELS[status];
  }

  processingLabel(status: VideoSummary['processingStatus']): string {
    return PROCESSING_LABELS[status];
  }

  transcriptStyle(status: VideoSummary['transcriptStatus']): string {
    return TRANSCRIPT_STYLES[status];
  }

  processingStyle(status: VideoSummary['processingStatus']): string {
    return PROCESSING_STYLES[status];
  }
}
