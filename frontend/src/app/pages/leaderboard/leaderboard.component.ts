import { Component, inject, signal, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../api/api.service';
import { BackendRecoveryService } from '../../services/backend-recovery.service';
import { SpyChartComponent } from '../../components/spy-chart/spy-chart.component';
import type { Channel, ChannelStats } from '../../api/types';

interface ChannelRow extends Channel {
  stats: ChannelStats | null;
}

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, RouterLink, SpyChartComponent],
  template: `
    <div class="max-w-screen-2xl mx-auto px-6 py-10">
      <div class="flex items-center justify-between mb-8">
        <div>
          <p class="text-gray-500 text-sm">Finance YouTubers ranked by historical stock pick performance</p>
        </div>
        @if (!error()) {
          <button
            (click)="handleIngest()"
            [disabled]="ingesting()"
            class="px-4 py-2 bg-primary-600 text-white rounded-lg text-sm font-semibold
                   hover:bg-primary-700 disabled:opacity-50 transition-colors shadow-sm"
          >
            {{ ingesting() ? 'Running…' : 'Run Ingestion' }}
          </button>
        }
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
                    No channels yet. Run ingestion to get started.
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
  readonly ingesting = signal(false);

  ngOnInit(): void {
    this.load();
  }

  ngOnDestroy(): void {
    this.recovery.stopPolling();
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

  handleIngest(): void {
    this.ingesting.set(true);
    this.api.triggerIngestion().subscribe({
      next: () => {
        this.ingesting.set(false);
        this.load();
      },
      error: () => {
        this.ingesting.set(false);
      },
    });
  }
}
