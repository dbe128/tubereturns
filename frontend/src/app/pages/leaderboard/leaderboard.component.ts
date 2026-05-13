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
import type { Channel, ChannelStats, ChannelSearchResult, PipelineStepStatus, PendingNotification } from '../../api/types';

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
        @if (auth.isAuthenticated && !showAddForm()) {
          <div class="mb-4 flex justify-end">
            <button
              (click)="openAddForm()"
              class="px-4 py-2 bg-gray-800 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 transition-colors"
            >+ Add Channel</button>
          </div>
        }

        @if (auth.isAuthenticated && showAddForm()) {
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
            <div class="flex items-center gap-4 mt-2">
              <label class="flex items-center gap-2 text-xs text-gray-500 select-none cursor-pointer w-fit">
                <input type="checkbox" [(ngModel)]="filterByKeywords" (change)="onSearchInput()" class="accent-primary-600" />
                Filter by finance keywords
              </label>
              <label class="flex items-center gap-2 text-xs text-gray-500 select-none cursor-pointer w-fit">
                <input type="checkbox" [(ngModel)]="notifyOnComplete" class="accent-primary-600" />
                Notify me when processed
              </label>
            </div>
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
                        @let stats = formatSearchResultStats(result);
                        @if (stats) {
                          <p class="text-xs text-gray-500 truncate mt-0.5">{{ stats }}</p>
                        }
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
                <th class="px-6 py-4 text-right">Subscribers</th>
                <th class="px-6 py-4 text-right">Videos</th>
                <th class="px-6 py-4 text-right">Processed</th>
                <th class="px-6 py-4">
                  <span class="text-primary-600">▲</span> Buy Picks
                </th>
                <th class="px-6 py-4">
                  <span class="text-danger-500">▼</span> Sell Picks
                </th>
                <th class="px-6 py-4">Actions</th>
              </tr>
            </thead>
            <tbody>
              @if (rows().length === 0) {
                <tr>
                  <td colspan="7" class="px-6 py-16 text-center text-gray-400 text-sm">
                    No channels yet. Add a channel to get started.
                  </td>
                </tr>
              } @else {
                @for (row of rows(); track row.handle; let i = $index) {
                  <tr class="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                    <td class="px-6 py-4 text-gray-300 font-mono text-sm">{{ i + 1 }}</td>
                    <td class="px-6 py-4">
                      <div class="flex items-center gap-2">
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
                        @if (isNotFullyProcessed(row)) {
                          <span class="relative group/tip flex-shrink-0">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                              Processing...
                            </span>
                          </span>
                        }
                      </div>
                    </td>
                    <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {{ row.subscriberCount != null ? formatSubscriberCount(row.subscriberCount) : '—' }}
                    </td>
                    <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {{ row.stats?.totalVideos ?? '—' }}
                    </td>
                    <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {{ row.stats?.processedVideos ?? '—' }}
                    </td>
                    <td class="px-6 py-4 text-sm font-mono text-primary-600 font-medium">
                      @if (row.stats && visiblePicks(row.stats.buyPicks).length > 0) {
                        @for (ticker of visiblePicks(row.stats.buyPicks); track ticker; let last = $last) {
                          <span class="inline-block whitespace-nowrap">
                            <span class="relative group/tk inline-block">{{ ticker }}
                              @if (tickerMap()[ticker]) {
                                <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs font-normal text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tk:opacity-100 transition-opacity z-50">{{ tickerMap()[ticker] }}</span>
                              }
                            </span>@if (auth.isAdmin && unknownTickers().has(ticker)) {<span class="relative group/unk inline-block text-yellow-500 ml-0.5 font-normal cursor-default text-3xl leading-none">⚠<span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/unk:opacity-100 transition-opacity z-50">Unknown Stock</span></span>}@if (!last) {, }
                          </span>
                        }
                      } @else {
                        <span class="text-gray-200 font-normal">—</span>
                      }
                    </td>
                    <td class="px-6 py-4 text-sm font-mono text-danger-500 font-medium">
                      @if (row.stats && visiblePicks(row.stats.sellPicks).length > 0) {
                        @for (ticker of visiblePicks(row.stats.sellPicks); track ticker; let last = $last) {
                          <span class="inline-block whitespace-nowrap">
                            <span class="relative group/tk inline-block">{{ ticker }}
                              @if (tickerMap()[ticker]) {
                                <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs font-normal text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tk:opacity-100 transition-opacity z-50">{{ tickerMap()[ticker] }}</span>
                              }
                            </span>@if (auth.isAdmin && unknownTickers().has(ticker)) {<span class="relative group/unk inline-block text-yellow-500 ml-0.5 font-normal cursor-default text-3xl leading-none">⚠<span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/unk:opacity-100 transition-opacity z-50">Unknown Stock</span></span>}@if (!last) {, }
                          </span>
                        }
                      } @else {
                        <span class="text-gray-200 font-normal">—</span>
                      }
                    </td>
                    <td class="px-6 py-4">
                      <div class="flex items-center gap-2">
                        @if (isNotFullyProcessed(row) && auth.isAuthenticated) {
                          @if (togglingNotificationFor() === row.handle) {
                            <div class="animate-spin rounded-full h-6 w-6 border-2 border-amber-400 border-t-transparent"></div>
                          } @else if (myNotifiedHandles().has(row.handle)) {
                            <button
                              (click)="toggleNotification(row)"
                              class="relative group/tip text-amber-400 hover:text-amber-500 transition-colors"
                            >
                              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/>
                              </svg>
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                                Unsubscribe from notification
                              </span>
                            </button>
                          } @else {
                            <button
                              (click)="toggleNotification(row)"
                              class="relative group/tip text-gray-300 hover:text-amber-400 transition-colors"
                            >
                              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                              </svg>
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                                Notify me when done
                              </span>
                            </button>
                          }
                        }
                        @if (auth.isAdmin) {
                          <button
                            (click)="reprocessChannel(row.handle, row.channelName)"
                            class="relative group/tip text-gray-300 hover:text-amber-400 transition-colors"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                            </svg>
                            <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                              Re-extract all picks
                            </span>
                          </button>
                        }
                        @if (auth.isAdmin) {
                        <button
                          (click)="deleteChannel(row.handle, row.channelName)"
                          class="relative group/tip text-gray-300 hover:text-danger-500 transition-colors"
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                          </svg>
                          <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                            Delete channel
                          </span>
                        </button>
                        }
                      </div>
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
                  @if (step.ytbsdStats ? step.ytbsdStats.running : step.running) {
                    <span class="flex items-center gap-1.5 text-xs text-primary-600 font-medium">
                      <span class="animate-spin inline-block h-3 w-3 border border-primary-500 border-t-transparent rounded-full"></span>
                      Running@if (step.ytbsdStats?.currentBatchSize) { ({{ step.ytbsdStats!.currentBatchSize }})}
                    </span>
                  } @else if (step.fatalError) {
                    <span class="text-xs text-red-600 font-medium">Fatal Error</span>
                  } @else {
                    <span class="text-xs text-gray-300">Idle</span>
                  }
                </div>
                @if (step.ytbsdStats; as s) {
                  @if (s.running && s.currentPhase) {
                    <div class="mb-4">
                      @if (s.currentPhase === 'fetching_info') {
                        <p class="text-xs text-gray-500">Refreshing proxies...</p>
                      } @else if (s.currentPhase === 'downloading') {
                        <p class="text-xs text-gray-500 mb-1.5">Downloading: {{ s.currentCompleted }} / {{ s.currentTotal }}@if (s.currentPct !== null) { ({{ s.currentPct }}%)}</p>
                        <div class="w-full bg-gray-100 rounded-full h-1.5">
                          <div class="bg-primary-500 h-1.5 rounded-full transition-all duration-500" [style.width.%]="s.currentPct ?? 0"></div>
                        </div>
                      }
                    </div>
                  }
                }
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
                    <dt class="text-gray-400">Next scheduled run</dt>
                    <dd class="text-gray-700 font-mono">
                      {{ step.nextRunAt ? (step.nextRunAt | date:'HH:mm:ss, dd MMM') : '—' }}
                    </dd>
                  </div>
                  @if (step.queueSize !== null) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Items in queue</dt>
                    <dd class="text-gray-700 font-mono">{{ step.queueSize }}</dd>
                  </div>
                  }
                  @if (step.fatalError) {
                  <div class="flex justify-between gap-4">
                    <dt class="text-gray-400 shrink-0">Error</dt>
                    <dd class="text-red-600 font-mono text-right break-all">{{ step.fatalError }}</dd>
                  </div>
                  }
                  @if (step.aiModelStatus; as ai) {
                  <div class="flex justify-between gap-2">
                    <dt class="text-gray-400 shrink-0">AI model</dt>
                    <dd class="text-gray-700 font-mono text-right break-all text-xs">
                      <span class="text-gray-400">#{{ ai.currentIndex }}</span> {{ ai.currentModel }}
                    </dd>
                  </div>
                  @if (ai.model0ResetAt) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400 shrink-0">Model reset at</dt>
                    <dd class="text-gray-700 font-mono">{{ ai.model0ResetAt | date:'HH:mm:ss, dd MMM' }}</dd>
                  </div>
                  }
                  }
                  @if (step.ytbsdStats) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">YTBSD runs</dt>
                    <dd class="text-gray-700 font-mono">
                      {{ step.ytbsdStats.totalRuns }}
                      <span class="text-green-600">({{ step.ytbsdStats.successfulRuns }} ok</span>
                      @if (step.ytbsdStats.failedRuns > 0) {
                        <span class="text-red-500">, {{ step.ytbsdStats.failedRuns }} failed</span>
                      }
                      <span class="text-green-600">)</span>
                    </dd>
                  </div>
                  @if (step.ytbsdStats.lastDurationMs !== null) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last ytbsd run</dt>
                    <dd class="text-gray-700 font-mono">{{ (step.ytbsdStats.lastDurationMs / 1000) | number:'1.1-1' }}s</dd>
                  </div>
                  }
                  @if (step.ytbsdStats.lastBatchSize !== null) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last batch size</dt>
                    <dd class="text-gray-700 font-mono">{{ step.ytbsdStats.lastBatchSize }}</dd>
                  </div>
                  }
                  }
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last processed</dt>
                    @if (step.ytbsdStats) {
                      <dd class="font-mono" [class]="step.limit !== null && step.ytbsdStats.lastBatchSize !== null && step.ytbsdStats.lastBatchSize >= step.limit ? 'text-amber-600' : 'text-gray-700'">
                        {{ step.ytbsdStats.lastBatchSize !== null ? (step.limit !== null ? step.ytbsdStats.lastBatchSize + ' / ' + step.limit : step.ytbsdStats.lastBatchSize) : '—' }}
                      </dd>
                    } @else {
                      <dd class="font-mono" [class]="step.limit !== null && step.lastRunCount !== null && step.lastRunCount >= step.limit ? 'text-amber-600' : 'text-gray-700'">
                        {{ step.lastRunCount !== null ? (step.limit !== null ? step.lastRunCount + ' / ' + step.limit : step.lastRunCount) : '—' }}
                      </dd>
                    }
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last run duration</dt>
                    <dd class="text-gray-700 font-mono">{{ stepDuration(step) ?? '—' }}</dd>
                  </div>

                </dl>
                <button
                  (click)="triggerStep(step.step)"
                  [disabled]="(step.ytbsdStats ? step.ytbsdStats.running : step.running) || disabledSteps().has(step.step)"
                  class="w-full px-3 py-1.5 bg-gray-800 text-white rounded-lg text-xs font-semibold
                         hover:bg-gray-700 disabled:opacity-40 transition-colors"
                >
                  Run now
                </button>
              </div>
            }
          </div>
        </div>

        <div class="mt-8">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-xs font-semibold text-gray-400 uppercase tracking-wider">Notifications</h2>
            <button
              (click)="triggerNotifications()"
              [disabled]="triggeringNotifications()"
              class="px-3 py-1.5 bg-gray-800 text-white rounded-lg text-xs font-semibold hover:bg-gray-700 disabled:opacity-40 transition-colors"
            >Check &amp; Send</button>
          </div>
          @if (pendingNotifications().length === 0) {
            <p class="text-xs text-gray-400">No pending notifications.</p>
          } @else {
            <div class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
              <table class="w-full text-xs">
                <thead class="bg-gray-50 text-gray-400 uppercase tracking-wider">
                  <tr>
                    <th class="px-4 py-2 text-left font-medium">Channel</th>
                    <th class="px-4 py-2 text-left font-medium">User</th>
                    <th class="px-4 py-2 text-left font-medium">Requested at</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (n of pendingNotifications(); track n.channelHandle + n.userEmail) {
                    <tr class="hover:bg-gray-50">
                      <td class="px-4 py-2 font-medium text-gray-800">{{ n.channelName }} <span class="text-gray-400">({{ n.channelHandle }})</span></td>
                      <td class="px-4 py-2 text-gray-600">{{ n.userEmail }}</td>
                      <td class="px-4 py-2 font-mono text-gray-500">{{ n.requestedAt | date:'HH:mm:ss, dd MMM' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
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
  readonly pendingNotifications = signal<PendingNotification[]>([]);
  readonly triggeringNotifications = signal(false);
  readonly tickerMap = signal<Record<string, string>>({});
  readonly unknownTickers = signal<ReadonlySet<string>>(new Set());
  readonly myNotifiedHandles = signal<Set<string>>(new Set());
  readonly togglingNotificationFor = signal<string | null>(null);
  readonly showAddForm = signal(false);
  @ViewChild('searchInput') private searchInputRef?: ElementRef<HTMLInputElement>;
  readonly searchResults = signal<ChannelSearchResult[]>([]);
  readonly searching = signal(false);
  readonly addingChannelId = signal<string | null>(null);
  readonly addError = signal<string | null>(null);
  searchQuery = '';
  filterByKeywords = true;
  notifyOnComplete = true;
  private readonly searchSubject = new Subject<string>();
  private searchSub?: Subscription;

  private statusPollSub?: Subscription;
  private fastPollSub?: Subscription;

  ngOnInit(): void {
    this.load();
    this.api.getTickers().subscribe({ next: (d) => { this.tickerMap.set(d.companies); this.unknownTickers.set(new Set(d.unknownTickers)); }, error: () => {} });
    if (this.auth.isAuthenticated) {
      this.loadMyNotifications();
    }
    if (this.auth.isAdmin) {
      this.loadPipelineStatus();
      this.loadPendingNotifications();
      this.statusPollSub = interval(15000).subscribe(() => { this.loadPipelineStatus(); this.loadPendingNotifications(); });
    }
    this.searchSub = this.searchSubject.pipe(
      debounceTime(400),
      switchMap((q) => q.trim().length >= 2 ? this.api.searchChannels(q, this.filterByKeywords).pipe(catchError(() => of<ChannelSearchResult[]>([]))) : of<ChannelSearchResult[]>([])),
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

  loadMyNotifications(): void {
    this.api.getMyChannelNotifications().subscribe({
      next: (handles) => this.myNotifiedHandles.set(new Set(handles)),
      error: () => {},
    });
  }

  isNotFullyProcessed(row: ChannelRow): boolean {
    if (!row.discoveryComplete) { return true; }
    if (row.stats && row.stats.processedVideos < row.stats.totalVideos) { return true; }
    return false;
  }

  toggleNotification(row: ChannelRow): void {
    if (this.togglingNotificationFor() !== null) { return; }
    const handle = row.handle;
    const subscribed = this.myNotifiedHandles().has(handle);
    this.togglingNotificationFor.set(handle);
    const action$ = subscribed
      ? this.api.unsubscribeFromChannelNotification(handle)
      : this.api.subscribeToChannelNotification(handle);
    action$.subscribe({
      next: () => {
        this.myNotifiedHandles.update((s) => {
          const next = new Set(s);
          if (subscribed) { next.delete(handle); } else { next.add(handle); }
          return next;
        });
        this.togglingNotificationFor.set(null);
      },
      error: () => this.togglingNotificationFor.set(null),
    });
  }

  loadPipelineStatus(): void {
    this.api.getPipelineStatus().subscribe({
      next: (status) => {
        this.pipelineStatus.set(status);
        if (!this.fastPollSub || this.fastPollSub.closed) {
          const transcript = status.find((s) => s.step === 'transcript');
          if (transcript?.ytbsdStats?.running) {
            this.pollUntilDone('transcript');
          }
        }
      },
      error: () => {},
    });
  }

  loadPendingNotifications(): void {
    this.api.getPendingNotifications().subscribe({
      next: (notifications) => this.pendingNotifications.set(notifications),
      error: () => {},
    });
  }

  triggerNotifications(): void {
    this.triggeringNotifications.set(true);
    this.api.triggerNotificationCheck().subscribe({
      next: () => { this.loadPendingNotifications(); this.triggeringNotifications.set(false); },
      error: () => this.triggeringNotifications.set(false),
    });
  }

  triggerStep(step: string): void {
    this.disabledSteps.update((s) => new Set([...s, step]));
    setTimeout(() => this.disabledSteps.update((s) => { const n = new Set(s); n.delete(step); return n; }), 5000);
    this.api.triggerPipelineStep(step).subscribe({
      next: () => this.pollUntilDone(step),
      error: () => {
        this.disabledSteps.update((s) => { const n = new Set(s); n.delete(step); return n; });
      },
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

  formatSearchResultStats(result: ChannelSearchResult): string {
    const parts: string[] = [];
    if (result.subscriberCount != null) {
      parts.push(this.formatSubscriberCount(result.subscriberCount) + ' subs');
    }
    if (result.videoCount != null) {
      parts.push(result.videoCount.toLocaleString() + ' videos');
    }
    if (result.channelCreatedAt) {
      parts.push('since ' + this.formatShortDate(result.channelCreatedAt));
    }
    if (result.latestVideoAt) {
      parts.push('last ' + this.formatShortDate(result.latestVideoAt));
    }
    return parts.join(' · ');
  }

  formatSubscriberCount(count: number): string {
    if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1)}M`;
    if (count >= 1_000) return `${Math.round(count / 1_000)}K`;
    return count.toLocaleString();
  }

  private formatShortDate(dateStr: string): string {
    return new Date(dateStr + 'T12:00:00').toLocaleDateString('en-US', { year: 'numeric', month: 'short' });
  }

  selectChannel(result: ChannelSearchResult): void {
    this.addingChannelId.set(result.handle);
    this.addError.set(null);
    this.api.addChannel(result.handle, result.channelName, result.channelUrl, result.thumbnailUrl ?? '', result.description ?? '', result.subscriberCount, this.notifyOnComplete).subscribe({
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

  reprocessChannel(handle: string, channelName: string): void {
    if (!confirm(`Re-extract all picks for "${channelName}"? This will delete all existing picks and re-run extraction.`)) {
      return;
    }
    this.api.reprocessChannel(handle).subscribe({
      next: () => this.load(),
      error: () => alert(`Failed to reprocess "${channelName}". Please try again.`),
    });
  }

  deleteChannel(channelId: string, channelName: string): void {
    if (!confirm(`Remove "${channelName}" from TubeReturns? This cannot be undone from the UI.`)) {
      return;
    }
    this.api.deleteChannel(channelId).subscribe({
      next: () => this.load(),
      error: () => {
        this.load();
        alert(`Failed to remove "${channelName}". Please try again.`);
      },
    });
  }

  visiblePicks(tickers: string[]): string[] {
    if (this.auth.isAdmin) {
      return tickers;
    }
    return tickers.filter(t => !this.unknownTickers().has(t));
  }

  stepDuration(step: PipelineStepStatus): string | null {
    if (step.lastRunDurationMs === null || step.lastRunDurationMs === undefined) {
      return null;
    }
    const totalSeconds = Math.round(step.lastRunDurationMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
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
