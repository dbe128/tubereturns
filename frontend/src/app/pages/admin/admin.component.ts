import { Component, inject, signal, computed, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';
import { ChannelStoreService } from '../../services/channel-store.service';
import type { Channel, ChannelSuggestion, PipelineStepStatus, NotificationsStatus, UnknownStock } from '../../api/types';

interface UnknownStockRow extends UnknownStock {
  editTicker: string;
  editCurrency: string;
  saving: boolean;
}

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    @if (toast()) {
      <div class="fixed top-6 right-6 z-50 max-w-sm px-4 py-3 rounded-xl shadow-lg text-sm font-medium text-white"
           [class]="toast()!.type === 'success' ? 'bg-green-600' : 'bg-red-600'">
        {{ toast()!.message }}
      </div>
    }
    @if (confirmDialog()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" (click)="confirmDialog.set(null)">
        <div class="bg-gray-900 border border-gray-700 rounded-xl shadow-xl p-6 max-w-sm w-full mx-4" (click)="$event.stopPropagation()">
          <p class="text-sm text-gray-300 mb-6">{{ confirmDialog()!.message }}</p>
          <div class="flex justify-end gap-3">
            <button (click)="confirmDialog.set(null)" class="px-4 py-2 text-sm text-gray-400 hover:text-gray-200 transition-colors font-medium">Cancel</button>
            <button (click)="runConfirm()" class="px-4 py-2 text-sm rounded-lg font-semibold text-white transition-colors"
                    [class]="confirmDialog()!.destructive ? 'bg-red-600 hover:bg-red-700' : 'bg-gray-800 hover:bg-gray-700'">Confirm</button>
          </div>
        </div>
      </div>
    }
    <div class="bg-gray-100 w-full min-h-screen">
      <div class="max-w-screen-2xl mx-auto px-4 md:px-6 py-10">
        <div class="bg-white rounded-2xl shadow-sm border border-gray-200 p-5 md:p-8">
        <div class="flex items-center justify-between mb-8">
          <h1 class="text-2xl font-black text-gray-900">Admin Dashboard</h1>
          <span class="text-xs text-gray-400 font-mono">v{{ version() }}</span>
        </div>

        <div class="flex flex-wrap items-center gap-3 mb-10">
          <input
            [ngModel]="adminAddInput()"
            (ngModelChange)="adminAddInput.set($event)"
            (keydown.enter)="addAdminChannel()"
            placeholder="Handle or YouTube URL — e.g. @EverythingMoney"
            class="flex-1 border border-gray-200 bg-gray-50 text-gray-900 placeholder-gray-400 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-green-500"
          />
          <button
            (click)="addAdminChannel()"
            [disabled]="adminAdding() || !adminAddInput().trim()"
            class="px-4 py-2 bg-gray-800 text-white rounded-lg text-sm font-semibold hover:bg-gray-700 disabled:opacity-40 transition-colors whitespace-nowrap flex items-center gap-2"
          >
            @if (adminAdding()) {
              <span class="inline-block h-4 w-4 rounded-full border-2 border-white border-t-transparent animate-spin"></span>
              Adding…
            } @else {
              Add Channel
            }
          </button>
        </div>

        @if (loading()) {
          <div class="flex justify-center py-20">
            <div class="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent"></div>
          </div>
        }

        @if (!loading() && inProgressChannels().length > 0) {
          <div class="mb-10">
            <h2 class="text-xs font-semibold text-gray-600 uppercase tracking-wider mb-4">Channels Being Processed</h2>
            <!-- mobile cards -->
            <div class="md:hidden flex flex-col gap-3">
              @for (row of inProgressSorted(); track row.handle) {
                <div class="bg-gray-900 border border-gray-700 rounded-xl p-4">
                  <a [routerLink]="['/channel', row.handle]" class="flex items-center gap-3 mb-3 group">
                    @if (row.hasThumbnail) {
                      <img [src]="'/api/channels/' + row.handle + '/thumbnail'" [alt]="row.channelName"
                           class="w-9 h-9 rounded-full object-cover flex-shrink-0 ring-2 ring-gray-700" />
                    } @else {
                      <div class="w-9 h-9 rounded-full bg-gray-700 flex-shrink-0"></div>
                    }
                    <span class="font-semibold text-white group-hover:text-green-400 transition-colors truncate">{{ row.channelName }}</span>
                  </a>
                  <div class="flex items-center gap-4 text-xs font-mono text-gray-400 mb-3">
                    <span>{{ row.subscriberCount != null ? formatSubscriberCount(row.subscriberCount) : '—' }} subs</span>
                    <span>{{ row.processedVideos }}/{{ row.totalVideos }} videos</span>
                    <span class="ml-auto">{{ formatProgress(row) }}</span>
                  </div>
                  <div class="flex items-center gap-2">
                    @if (togglingNotificationFor() === row.handle) {
                      <div class="animate-spin rounded-full h-5 w-5 border-2 border-amber-400 border-t-transparent"></div>
                    } @else if (myNotifiedHandles().has(row.handle)) {
                      <button (click)="toggleNotification(row)" class="flex-1 py-1.5 bg-amber-900/40 text-amber-400 rounded text-xs font-semibold hover:bg-amber-900/60 transition-colors">Unsubscribe</button>
                    } @else {
                      <button (click)="toggleNotification(row)" class="flex-1 py-1.5 bg-gray-800 text-gray-300 rounded text-xs font-semibold hover:text-amber-400 transition-colors">Notify me</button>
                    }
                    <button (click)="reprocessChannel(row.handle, row.channelName)" class="flex-1 py-1.5 bg-gray-800 text-gray-300 rounded text-xs font-semibold hover:text-amber-400 transition-colors">Re-extract</button>
                    <button (click)="deleteChannel(row.handle, row.channelName)" class="flex-1 py-1.5 bg-gray-800 text-gray-300 rounded text-xs font-semibold hover:text-danger-500 transition-colors">Delete</button>
                  </div>
                </div>
              }
            </div>
            <!-- desktop table -->
            <div class="hidden md:block bg-gray-900 rounded-xl shadow-sm border border-gray-700 overflow-x-auto">
              <table class="w-full min-w-max">
                <thead>
                  <tr class="border-b border-gray-700 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    <th class="px-6 py-4">Channel</th>
                    <th class="px-6 py-4 text-right">Subscribers</th>
                    <th class="px-6 py-4 text-right">Videos</th>
                    <th class="px-6 py-4 text-right">Progress</th>
                    <th class="px-6 py-4">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (row of inProgressSorted(); track row.handle) {
                    <tr class="border-b border-gray-700 hover:bg-gray-800/60 transition-colors">
                      <td class="px-6 py-4">
                        <a [routerLink]="['/channel', row.handle]" class="flex items-center gap-3 group">
                          @if (row.hasThumbnail) {
                            <img [src]="'/api/channels/' + row.handle + '/thumbnail'" [alt]="row.channelName"
                                 class="w-9 h-9 rounded-full object-cover flex-shrink-0 ring-2 ring-gray-700" />
                          } @else {
                            <div class="w-9 h-9 rounded-full bg-gray-700 flex-shrink-0"></div>
                          }
                          <span class="font-semibold text-white group-hover:text-green-400 transition-colors">{{ row.channelName }}</span>
                        </a>
                      </td>
                      <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                        {{ row.subscriberCount != null ? formatSubscriberCount(row.subscriberCount) : '—' }}
                      </td>
                      <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">{{ row.processedVideos }}/{{ row.totalVideos }}</td>
                      <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">{{ formatProgress(row) }}</td>
                      <td class="px-6 py-4">
                        <div class="flex items-center gap-2">
                          @if (togglingNotificationFor() === row.handle) {
                            <div class="animate-spin rounded-full h-6 w-6 border-2 border-amber-400 border-t-transparent"></div>
                          } @else if (myNotifiedHandles().has(row.handle)) {
                            <button (click)="toggleNotification(row)" class="relative group/tip text-amber-400 hover:text-amber-500 transition-colors">
                              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/>
                              </svg>
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                                Unsubscribe from notification
                              </span>
                            </button>
                          } @else {
                            <button (click)="toggleNotification(row)" class="relative group/tip text-gray-300 hover:text-amber-400 transition-colors">
                              <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                              </svg>
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                                Notify me when done
                              </span>
                            </button>
                          }
                          <button (click)="reprocessChannel(row.handle, row.channelName)" class="relative group/tip text-gray-300 hover:text-amber-400 transition-colors">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
                            </svg>
                            <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                              Re-extract all picks
                            </span>
                          </button>
                          <button (click)="deleteChannel(row.handle, row.channelName)" class="relative group/tip text-gray-300 hover:text-danger-500 transition-colors">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                            <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                              Delete channel
                            </span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

        <div class="mt-10">
          <h2 class="text-xs font-semibold text-gray-600 uppercase tracking-wider mb-4">Pipeline</h2>
          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
            @for (step of pipelineStatus(); track step.step) {
              <div class="bg-gray-900 rounded-xl shadow-sm border border-gray-700 p-5">
                <div class="flex items-center justify-between mb-4">
                  <h3 class="font-semibold text-white text-sm">{{ step.label }}</h3>
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
                    <dd class="text-gray-300 font-mono">{{ step.lastStartedAt ? (step.lastStartedAt | date:'HH:mm:ss, dd MMM') : '—' }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last finished</dt>
                    <dd class="text-gray-300 font-mono">{{ step.lastFinishedAt ? (step.lastFinishedAt | date:'HH:mm:ss, dd MMM') : '—' }}</dd>
                  </div>
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Next scheduled run</dt>
                    <dd class="text-gray-300 font-mono">{{ step.nextRunAt ? (step.nextRunAt | date:'HH:mm:ss, dd MMM') : '—' }}</dd>
                  </div>
                  @if (step.queueSize !== null) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Items in queue</dt>
                    <dd class="text-gray-300 font-mono">{{ step.queueSize }}</dd>
                  </div>
                  }
                  @if (step.activeWorkers !== null) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Active workers</dt>
                    <dd class="text-gray-300 font-mono">{{ step.activeWorkers }}</dd>
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
                    <dd class="text-gray-300 font-mono text-right break-all text-xs">
                      <span class="text-gray-400">#{{ ai.currentIndex }}</span> {{ ai.currentModel }}
                    </dd>
                  </div>
                  @if (ai.model0ResetAt) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400 shrink-0">Model reset at</dt>
                    <dd class="text-gray-300 font-mono">{{ ai.model0ResetAt | date:'HH:mm:ss, dd MMM' }}</dd>
                  </div>
                  }
                  }
                  @if (step.ytbsdStats) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">YTBSD runs</dt>
                    <dd class="text-gray-300 font-mono">
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
                    <dd class="text-gray-300 font-mono">{{ (step.ytbsdStats.lastDurationMs / 1000) | number:'1.1-1' }}s</dd>
                  </div>
                  }
                  @if (step.ytbsdStats.lastBatchSize !== null) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last batch size</dt>
                    <dd class="text-gray-300 font-mono">{{ step.ytbsdStats.lastBatchSize }}</dd>
                  </div>
                  }
                  }
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last processed</dt>
                    @if (step.ytbsdStats) {
                      <dd class="font-mono" [class]="step.limit !== null && step.ytbsdStats.lastBatchSize !== null && step.ytbsdStats.lastBatchSize >= step.limit ? 'text-amber-600' : 'text-gray-300'">
                        {{ step.ytbsdStats.lastBatchSize !== null ? (step.limit !== null ? step.ytbsdStats.lastBatchSize + ' / ' + step.limit : step.ytbsdStats.lastBatchSize) : '—' }}
                      </dd>
                    } @else {
                      <dd class="font-mono" [class]="step.limit !== null && step.lastRunCount !== null && step.lastRunCount >= step.limit ? 'text-amber-600' : 'text-gray-300'">
                        {{ step.lastRunCount !== null ? (step.limit !== null ? step.lastRunCount + ' / ' + step.limit : step.lastRunCount) : '—' }}
                      </dd>
                    }
                  </div>
                  @if (step.aiModelStatus) {
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last OpenRouter call</dt>
                    <dd class="text-gray-300 font-mono">{{ step.aiModelStatus.lastCallDurationMs != null ? aiCallDuration(step.aiModelStatus.lastCallDurationMs) : '—' }}</dd>
                  </div>
                  }
                  <div class="flex justify-between">
                    <dt class="text-gray-400">Last run duration</dt>
                    <dd class="text-gray-300 font-mono">{{ stepDuration(step) ?? '—' }}</dd>
                  </div>
                </dl>
                <button
                  (click)="triggerStep(step.step)"
                  [disabled]="(step.ytbsdStats ? step.ytbsdStats.running : step.running) || disabledSteps().has(step.step)"
                  class="w-full px-3 py-1.5 bg-gray-800 text-white rounded-lg text-xs font-semibold hover:bg-gray-700 disabled:opacity-40 transition-colors"
                >
                  Run now
                </button>
              </div>
            }
          </div>
        </div>

        <div class="mt-8">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h2 class="text-xs font-semibold text-gray-600 uppercase tracking-wider">Notifications</h2>
              @if (notificationsStatus().lastRunAt) {
                <p class="text-xs text-gray-500 mt-0.5">Last run: {{ notificationsStatus().lastRunAt | date:'HH:mm:ss, dd MMM' }}</p>
              }
              @if (notificationsStatus().nextRunAt) {
                <p class="text-xs text-gray-500 mt-0.5">Next check: {{ notificationsStatus().nextRunAt | date:'HH:mm:ss, dd MMM' }}</p>
              }
            </div>
            <button
              (click)="triggerNotifications()"
              [disabled]="triggeringNotifications()"
              class="px-3 py-1.5 bg-gray-800 text-white rounded-lg text-xs font-semibold hover:bg-gray-700 disabled:opacity-40 transition-colors"
            >Check &amp; Send</button>
          </div>
          @if (notificationsStatus().items.length === 0) {
            <p class="text-xs text-gray-500">No pending notifications.</p>
          } @else {
            <div class="bg-gray-900 rounded-xl shadow-sm border border-gray-700 overflow-x-auto">
              <table class="w-full min-w-max text-xs">
                <thead class="bg-gray-800 text-gray-500 uppercase tracking-wider">
                  <tr>
                    <th class="px-4 py-2 text-left font-medium">Channel</th>
                    <th class="px-4 py-2 text-left font-medium">User</th>
                    <th class="px-4 py-2 text-left font-medium">Requested at</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (n of notificationsStatus().items; track n.channelHandle + n.userEmail) {
                    <tr class="hover:bg-gray-800/60">
                      <td class="px-4 py-2 font-medium text-white">{{ n.channelName }} <span class="text-gray-400">({{ n.channelHandle }})</span></td>
                      <td class="px-4 py-2 text-gray-400">{{ n.userEmail }}</td>
                      <td class="px-4 py-2 font-mono text-gray-500">{{ n.requestedAt | date:'HH:mm:ss, dd MMM' }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>

        <div class="mt-8">
          <h2 class="text-xs font-semibold text-gray-600 uppercase tracking-wider mb-4">Unknown Stocks</h2>
          @if (unknownStockRows().length === 0) {
            <p class="text-xs text-gray-500">No unreviewed unknown stocks.</p>
          } @else {
            <!-- mobile cards -->
            <div class="md:hidden flex flex-col gap-3">
              @for (row of unknownStockRows(); track row.id) {
                @let hasChanges = stockHasChanges(row);
                <div class="bg-gray-900 border border-gray-700 rounded-xl p-4 text-xs">
                  <div class="flex items-center gap-3 mb-3">
                    <input
                      [ngModel]="row.editTicker"
                      (ngModelChange)="updateStockRow(row.id, 'editTicker', $event)"
                      [disabled]="row.saving"
                      class="w-28 border border-gray-700 bg-gray-800 text-white rounded px-2 py-1 font-mono text-xs focus:outline-none focus:ring-1 focus:ring-green-500"
                    />
                    <span class="text-gray-400 flex-1 truncate">{{ row.companyName ?? '—' }}</span>
                    <span class="font-mono text-gray-300 flex-shrink-0">{{ row.pickCount }} picks</span>
                  </div>
                  <div class="flex items-center gap-3 mb-3">
                    <span class="text-gray-500">Currency</span>
                    <input
                      [ngModel]="row.editCurrency"
                      (ngModelChange)="updateStockRow(row.id, 'editCurrency', $event)"
                      [disabled]="row.saving"
                      maxlength="3"
                      class="w-16 border border-gray-700 bg-gray-800 text-white rounded px-2 py-1 font-mono text-xs focus:outline-none focus:ring-1 focus:ring-green-500"
                    />
                    <span class="font-mono text-gray-500 ml-auto">{{ row.createdAt | date:'dd MMM yyyy' }}</span>
                  </div>
                  <div class="flex gap-2">
                    <button
                      (click)="tryTicker(row)"
                      [disabled]="row.saving || (hasChanges && !row.editCurrency)"
                      class="flex-1 py-1.5 bg-primary-600 text-white rounded text-xs font-semibold hover:bg-primary-700 disabled:opacity-40 transition-colors"
                    >{{ hasChanges ? 'Fix' : 'Retry' }}</button>
                    <button
                      (click)="acceptUnknown(row)"
                      [disabled]="row.saving"
                      class="flex-1 py-1.5 bg-gray-700 text-white rounded text-xs font-semibold hover:bg-gray-600 disabled:opacity-40 transition-colors"
                    >Accept</button>
                  </div>
                </div>
              }
            </div>
            <!-- desktop table -->
            <div class="hidden md:block bg-gray-900 rounded-xl shadow-sm border border-gray-700 overflow-x-auto">
              <table class="w-full min-w-max text-xs">
                <thead class="bg-gray-800 text-gray-500 uppercase tracking-wider">
                  <tr>
                    <th class="px-4 py-2 text-left font-medium">Ticker</th>
                    <th class="px-4 py-2 text-left font-medium">Company</th>
                    <th class="px-4 py-2 text-right font-medium">Picks</th>
                    <th class="px-4 py-2 text-left font-medium">Currency</th>
                    <th class="px-4 py-2 text-left font-medium">Created at</th>
                    <th class="px-4 py-2 text-left font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (row of unknownStockRows(); track row.id) {
                    @let hasChanges = stockHasChanges(row);
                    <tr class="hover:bg-gray-800/60">
                      <td class="px-4 py-2">
                        <input
                          [ngModel]="row.editTicker"
                          (ngModelChange)="updateStockRow(row.id, 'editTicker', $event)"
                          [disabled]="row.saving"
                          class="w-28 border border-gray-700 bg-gray-800 text-white rounded px-2 py-1 font-mono text-xs focus:outline-none focus:ring-1 focus:ring-green-500"
                        />
                      </td>
                      <td class="px-4 py-2 text-gray-400">{{ row.companyName ?? '—' }}</td>
                      <td class="px-4 py-2 text-right font-mono text-gray-300">{{ row.pickCount }}</td>
                      <td class="px-4 py-2">
                        <input
                          [ngModel]="row.editCurrency"
                          (ngModelChange)="updateStockRow(row.id, 'editCurrency', $event)"
                          [disabled]="row.saving"
                          maxlength="3"
                          class="w-16 border border-gray-700 bg-gray-800 text-white rounded px-2 py-1 font-mono text-xs focus:outline-none focus:ring-1 focus:ring-green-500"
                        />
                      </td>
                      <td class="px-4 py-2 font-mono text-gray-500">{{ row.createdAt | date:'dd MMM yyyy' }}</td>
                      <td class="px-4 py-2">
                        <div class="flex gap-2 flex-wrap items-center">
                          <button
                            (click)="tryTicker(row)"
                            [disabled]="row.saving || (hasChanges && !row.editCurrency)"
                            class="px-2 py-1 bg-primary-600 text-white rounded text-xs font-semibold hover:bg-primary-700 disabled:opacity-40 transition-colors whitespace-nowrap"
                          >{{ hasChanges ? 'Fix' : 'Retry' }}</button>
                          <button
                            (click)="acceptUnknown(row)"
                            [disabled]="row.saving"
                            class="px-2 py-1 bg-gray-700 text-white rounded text-xs font-semibold hover:bg-gray-600 disabled:opacity-40 transition-colors whitespace-nowrap"
                          >Accept</button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>

        <div class="mt-8 pb-10">
          <h2 class="text-xs font-semibold text-gray-600 uppercase tracking-wider mb-4">Channel Suggestions</h2>
          @if (pendingChannelSuggestions().length === 0) {
            <p class="text-xs text-gray-500">No pending channel suggestions.</p>
          } @else {
            <!-- mobile cards -->
            <div class="md:hidden flex flex-col gap-3">
              @for (s of pendingChannelSuggestions(); track s.handle) {
                <div class="bg-gray-900 border border-gray-700 rounded-xl p-4 text-xs">
                  <div class="flex items-center gap-2 mb-3">
                    <img [src]="'/api/channel-suggestions/' + s.handle + '/thumbnail'"
                         [alt]="s.channelName"
                         (error)="hideImgOnError($event)"
                         class="w-8 h-8 rounded-full object-cover flex-shrink-0 ring-1 ring-gray-700" />
                    <div class="min-w-0 flex-1">
                      <p class="font-semibold text-white truncate">{{ s.channelName }}</p>
                      <p class="text-gray-400">&#64;{{ s.handle }}</p>
                    </div>
                  </div>
                  <div class="flex items-center gap-4 font-mono text-gray-400 mb-3">
                    <span>{{ s.subscriberCount != null ? formatSubscriberCount(s.subscriberCount) : '—' }} subs</span>
                    <span>{{ s.suggestionCount }} suggestion{{ s.suggestionCount === 1 ? '' : 's' }}</span>
                    <span class="ml-auto text-gray-500">{{ s.firstSuggestedAt | date:'dd MMM yyyy' }}</span>
                  </div>
                  <div class="flex gap-2">
                    <button (click)="approveSuggestion(s)"
                      class="flex-1 py-1.5 bg-primary-600 text-white rounded text-xs font-semibold hover:bg-primary-700 transition-colors">Add</button>
                    <button (click)="rejectSuggestion(s)"
                      class="flex-1 py-1.5 bg-red-600 text-white rounded text-xs font-semibold hover:bg-red-700 transition-colors">Reject</button>
                  </div>
                </div>
              }
            </div>
            <!-- desktop table -->
            <div class="hidden md:block bg-gray-900 rounded-xl shadow-sm border border-gray-700 overflow-x-auto">
              <table class="w-full min-w-max text-xs">
                <thead class="bg-gray-800 text-gray-500 uppercase tracking-wider">
                  <tr>
                    <th class="px-4 py-2 text-left font-medium">Channel</th>
                    <th class="px-4 py-2 text-right font-medium">Subscribers</th>
                    <th class="px-4 py-2 text-right font-medium">Suggestions</th>
                    <th class="px-4 py-2 text-left font-medium">First suggested</th>
                    <th class="px-4 py-2 text-left font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (s of pendingChannelSuggestions(); track s.handle) {
                    <tr class="hover:bg-gray-800/60">
                      <td class="px-4 py-2">
                        <div class="flex items-center gap-2">
                          <img [src]="'/api/channel-suggestions/' + s.handle + '/thumbnail'"
                               [alt]="s.channelName"
                               (error)="hideImgOnError($event)"
                               class="w-7 h-7 rounded-full object-cover flex-shrink-0 ring-1 ring-gray-700" />
                          <div>
                            <p class="font-semibold text-white">{{ s.channelName }}</p>
                            <p class="text-gray-400">&#64;{{ s.handle }}</p>
                          </div>
                        </div>
                      </td>
                      <td class="px-4 py-2 text-right font-mono text-gray-300">
                        {{ s.subscriberCount != null ? formatSubscriberCount(s.subscriberCount) : '—' }}
                      </td>
                      <td class="px-4 py-2 text-right font-mono text-gray-300">{{ s.suggestionCount }}</td>
                      <td class="px-4 py-2 font-mono text-gray-500">{{ s.firstSuggestedAt | date:'dd MMM yyyy' }}</td>
                      <td class="px-4 py-2">
                        <div class="flex gap-2">
                          <button
                            (click)="approveSuggestion(s)"
                            class="px-2 py-1 bg-primary-600 text-white rounded text-xs font-semibold hover:bg-primary-700 transition-colors"
                          >Add</button>
                          <button
                            (click)="rejectSuggestion(s)"
                            class="px-2 py-1 bg-red-600 text-white rounded text-xs font-semibold hover:bg-red-700 transition-colors"
                          >Reject</button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
        </div>
      </div>
    </div>
  `,
})
export class AdminComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  private readonly channelStore = inject(ChannelStoreService);
  private readonly router = inject(Router);

  readonly rows = signal<Channel[]>([]);
  readonly loading = signal(true);

  readonly inProgressChannels = computed(() =>
    this.rows().filter(r => !(r.discoveryComplete && r.totalVideos > 0 && r.processedVideos === r.totalVideos))
  );

  readonly inProgressSorted = computed(() =>
    [...this.inProgressChannels()].sort((a, b) => {
      const pctA = a.totalVideos === 0 ? 0 : a.processedVideos / a.totalVideos;
      const pctB = b.totalVideos === 0 ? 0 : b.processedVideos / b.totalVideos;
      return pctB - pctA;
    })
  );

  readonly pipelineStatus = signal<PipelineStepStatus[]>([]);
  readonly disabledSteps = signal<Set<string>>(new Set());
  readonly notificationsStatus = signal<NotificationsStatus>({ nextRunAt: null, lastRunAt: null, items: [] });
  readonly triggeringNotifications = signal(false);
  readonly unknownStockRows = signal<UnknownStockRow[]>([]);
  readonly pendingChannelSuggestions = signal<ChannelSuggestion[]>([]);
  readonly version = signal('…');
  readonly adminAddInput = signal('');
  readonly adminAdding = signal(false);
  readonly toast = signal<{ message: string; type: 'success' | 'error' } | null>(null);
  private toastTimer?: ReturnType<typeof setTimeout>;
  readonly confirmDialog = signal<{ message: string; destructive: boolean; onConfirm: () => void } | null>(null);
  readonly myNotifiedHandles = signal<Set<string>>(new Set());
  readonly togglingNotificationFor = signal<string | null>(null);

  private statusPollSub?: Subscription;
  private fastPollSub?: Subscription;

  ngOnInit(): void {
    if (!this.auth.isAdmin) {
      this.router.navigate(['/']);
      return;
    }
    this.api.getVersion().subscribe((v) => this.version.set(v));
    this.load();
    this.loadMyNotifications();
    this.loadPipelineStatus();
    this.loadPendingNotifications();
    this.loadUnknownStocks();
    this.loadPendingChannelSuggestions();
    this.statusPollSub = interval(15000).subscribe(() => {
      this.loadPipelineStatus();
      this.loadPendingNotifications();
      this.loadUnknownStocks();
      this.loadPendingChannelSuggestions();
    });
  }

  ngOnDestroy(): void {
    this.statusPollSub?.unsubscribe();
    this.fastPollSub?.unsubscribe();
    clearTimeout(this.toastTimer);
  }

  private load(): void {
    this.loading.set(true);
    this.api.getChannels().subscribe({
      next: (channels) => {
        this.rows.set(channels);
        this.channelStore.channels.set(channels);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadMyNotifications(): void {
    this.api.getMyChannelNotifications().subscribe({
      next: (handles) => this.myNotifiedHandles.set(new Set(handles)),
      error: () => {},
    });
  }

  toggleNotification(row: Channel): void {
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
      next: (status) => this.notificationsStatus.set(status),
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

  loadPendingChannelSuggestions(): void {
    this.api.getPendingChannelSuggestions().subscribe({
      next: (suggestions) => this.pendingChannelSuggestions.set(suggestions),
      error: () => {},
    });
  }

  approveSuggestion(s: ChannelSuggestion): void {
    this.api.addChannelSuggestion(s.handle).subscribe({
      next: () => { this.loadPendingChannelSuggestions(); this.load(); },
      error: () => this.showToast(`Failed to add "${s.channelName}".`, 'error'),
    });
  }

  rejectSuggestion(s: ChannelSuggestion): void {
    this.openConfirm(
      `Reject suggestion for "${s.channelName}"?`,
      true,
      () => this.api.rejectChannelSuggestion(s.handle).subscribe({
        next: () => this.loadPendingChannelSuggestions(),
        error: () => this.showToast(`Failed to reject "${s.channelName}".`, 'error'),
      }),
    );
  }

  addAdminChannel(): void {
    const raw = this.adminAddInput().trim();
    if (!raw) { return; }
    const handle = this.parseHandle(raw);
    this.adminAdding.set(true);
    this.api.resolveChannel(handle).subscribe({
      next: (channel) => {
        if (!channel) {
          this.adminAdding.set(false);
          this.showToast(`No YouTube channel found for "${handle}".`, 'error');
          return;
        }
        this.api.addChannel(channel.handle, channel.channelName, channel.channelUrl, channel.thumbnailUrl ?? '', channel.description ?? '', channel.subscriberCount, false, 'ADMIN').subscribe({
          next: () => {
            this.adminAdding.set(false);
            this.adminAddInput.set('');
            this.showToast(`${channel.channelName} added.`, 'success');
            this.load();
          },
          error: (err: unknown) => {
            this.adminAdding.set(false);
            this.showToast(String(err), 'error');
          },
        });
      },
      error: () => {
        this.adminAdding.set(false);
        this.showToast(`Failed to look up "${handle}" on YouTube.`, 'error');
      },
    });
  }

  private parseHandle(raw: string): string {
    const urlMatch = raw.match(/youtube\.com\/@([^/?&\s]+)/i);
    if (urlMatch) { return urlMatch[1]; }
    if (raw.startsWith('@')) { return raw.slice(1).trim(); }
    return raw.trim();
  }

  reprocessChannel(handle: string, channelName: string): void {
    this.openConfirm(
      `Re-extract all picks for "${channelName}"? This will delete all existing picks and re-run extraction.`,
      true,
      () => this.api.reprocessChannel(handle).subscribe({
        next: () => this.load(),
        error: () => this.showToast(`Failed to reprocess "${channelName}". Please try again.`, 'error'),
      }),
    );
  }

  deleteChannel(channelId: string, channelName: string): void {
    this.openConfirm(
      `Remove "${channelName}" from TubeReturns? This cannot be undone from the UI.`,
      true,
      () => this.api.deleteChannel(channelId).subscribe({
        next: () => this.load(),
        error: () => {
          this.load();
          this.showToast(`Failed to remove "${channelName}". Please try again.`, 'error');
        },
      }),
    );
  }

  loadUnknownStocks(): void {
    this.api.getUnknownStocks().subscribe({
      next: (stocks) => {
        const current = new Map(this.unknownStockRows().map((r) => [r.id, r]));
        this.unknownStockRows.set(stocks.map((s) => {
          const existing = current.get(s.id);
          return {
            ...s,
            editTicker: existing ? existing.editTicker : s.tickerSymbol,
            editCurrency: existing ? existing.editCurrency : (s.currency ?? ''),
            saving: existing ? existing.saving : false,
          };
        }));
      },
      error: () => {},
    });
  }

  updateStockRow(id: number, field: 'editTicker' | 'editCurrency', value: string): void {
    this.unknownStockRows.update((rows) => rows.map((r) => r.id === id ? { ...r, [field]: value } : r));
  }

  stockHasChanges(row: UnknownStockRow): boolean {
    return row.editTicker !== row.tickerSymbol || row.editCurrency !== (row.currency ?? '');
  }

  tryTicker(row: UnknownStockRow): void {
    this.unknownStockRows.update((rows) => rows.map((r) => r.id === row.id ? { ...r, saving: true } : r));
    this.api.tryTicker(row.id, row.editTicker, row.editCurrency).subscribe({
      next: (resp) => {
        this.showToast(resp.message, 'success');
        this.loadUnknownStocks();
      },
      error: (err: unknown) => {
        this.showToast(String(err), 'error');
        this.unknownStockRows.update((rows) => rows.map((r) => r.id === row.id ? { ...r, saving: false } : r));
      },
    });
  }

  acceptUnknown(row: UnknownStockRow): void {
    const label = row.companyName ?? row.tickerSymbol;
    this.openConfirm(
      `Mark "${label}" as accepted unknown? It will be hidden from this list.`,
      false,
      () => {
        this.unknownStockRows.update((rows) => rows.map((r) => r.id === row.id ? { ...r, saving: true } : r));
        this.api.acceptUnknown(row.id).subscribe({
          next: () => this.loadUnknownStocks(),
          error: () => {
            this.showToast(`Failed to accept "${label}". Please try again.`, 'error');
            this.unknownStockRows.update((rows) => rows.map((r) => r.id === row.id ? { ...r, saving: false } : r));
          },
        });
      },
    );
  }

  formatSubscriberCount(count: number): string {
    if (count >= 1_000_000) { return `${(count / 1_000_000).toFixed(1)}M`; }
    if (count >= 1_000) { return `${Math.round(count / 1_000)}K`; }
    return count.toLocaleString();
  }

  formatProgress(row: Channel): string {
    if (row.totalVideos === 0) { return '–'; }
    return `${((row.processedVideos / row.totalVideos) * 100).toFixed(2)}%`;
  }

  stepDuration(step: PipelineStepStatus): string | null {
    if (step.lastRunDurationMs === null || step.lastRunDurationMs === undefined) { return null; }
    const totalSeconds = Math.round(step.lastRunDurationMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
  }

  aiCallDuration(ms: number): string {
    const totalSeconds = Math.round(ms / 1000);
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

  private showToast(message: string, type: 'success' | 'error'): void {
    clearTimeout(this.toastTimer);
    this.toast.set({ message, type });
    this.toastTimer = setTimeout(() => this.toast.set(null), 6000);
  }

  private openConfirm(message: string, destructive: boolean, onConfirm: () => void): void {
    this.confirmDialog.set({ message, destructive, onConfirm });
  }

  runConfirm(): void {
    const dialog = this.confirmDialog();
    this.confirmDialog.set(null);
    dialog?.onConfirm();
  }

  hideImgOnError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }
}
