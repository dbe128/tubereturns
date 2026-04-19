import { Component, inject, signal, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, interval, of, Subscription } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { ApiService } from '../../api/api.service';
import { BackendRecoveryService } from '../../services/backend-recovery.service';
import { SpyChartComponent } from '../../components/spy-chart/spy-chart.component';
import type { Channel, ChannelStats, PipelineStepStatus } from '../../api/types';

interface ChannelRow extends Channel {
  stats: ChannelStats | null;
}

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, RouterLink, SpyChartComponent],
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
              </tr>
            </thead>
            <tbody>
              @if (rows().length === 0) {
                <tr>
                  <td colspan="6" class="px-6 py-16 text-center text-gray-400 text-sm">
                    No channels yet. Trigger Video Discovery to get started.
                  </td>
                </tr>
              } @else {
                @for (row of rows(); track row.youtubeChannelId; let i = $index) {
                  <tr class="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                    <td class="px-6 py-4 text-gray-300 font-mono text-sm">{{ i + 1 }}</td>
                    <td class="px-6 py-4">
                      <a
                        [routerLink]="['/channel', row.youtubeChannelId]"
                        class="flex items-center gap-3 group"
                      >
                        @if (row.hasThumbnail) {
                          <img
                            [src]="'/api/channels/' + row.youtubeChannelId + '/thumbnail'"
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
                  </tr>
                }
              }
            </tbody>
          </table>
        </div>
      }

      @if (!error()) {
        <app-spy-chart />

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
    </div>
  `,
})
export class LeaderboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly recovery = inject(BackendRecoveryService);

  readonly rows = signal<ChannelRow[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly pipelineStatus = signal<PipelineStepStatus[]>([]);
  readonly disabledSteps = signal<Set<string>>(new Set());

  private statusPollSub?: Subscription;
  private fastPollSub?: Subscription;

  ngOnInit(): void {
    this.load();
    this.loadPipelineStatus();
    this.statusPollSub = interval(15000).subscribe(() => this.loadPipelineStatus());
  }

  ngOnDestroy(): void {
    this.recovery.stopPolling();
    this.statusPollSub?.unsubscribe();
    this.fastPollSub?.unsubscribe();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.recovery.stopPolling();

    this.api.getChannels().subscribe({
      next: (channels) => {
        const stats$ = channels.map((ch) =>
          this.api.getChannelStats(ch.youtubeChannelId).pipe(catchError(() => of(null))),
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
