import { Component, inject, signal, computed, OnDestroy, OnInit } from '@angular/core';
import { DeletedCountComponent } from '../../components/deleted-count/deleted-count.component';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink, ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';
import { BackendRecoveryService } from '../../services/backend-recovery.service';
import { ChannelStoreService } from '../../services/channel-store.service';
import { FeatureFlagService } from '../../services/feature-flag.service';
import type { Channel, MyChannelSuggestion, TrendingPick } from '../../api/types';

@Component({
  selector: 'app-leaderboard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, DeletedCountComponent],
  styles: [`
    @keyframes wiggle {
      0%, 100% { transform: rotate(-2deg); }
      50% { transform: rotate(2deg); }
    }
    .wiggle { animation: wiggle 0.7s ease-in-out infinite; display: inline-block; }
  `],
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
            <button
              (click)="confirmDialog.set(null)"
              class="px-4 py-2 text-sm text-gray-400 hover:text-gray-200 transition-colors font-medium"
            >Cancel</button>
            <button
              (click)="runConfirm()"
              class="px-4 py-2 text-sm rounded-lg font-semibold text-white transition-colors"
              [class]="confirmDialog()!.destructive ? 'bg-red-600 hover:bg-red-700' : 'bg-gray-800 hover:bg-gray-700'"
            >Confirm</button>
          </div>
        </div>
      </div>
    }
    <div class="bg-gray-950 w-full">
      <div class="max-w-screen-2xl mx-auto px-4 md:px-8 py-10 md:py-14 text-center">
        <div class="flex items-center justify-center gap-4 mb-10 text-xs font-bold tracking-[0.18em] uppercase">
          <span class="flex items-center gap-1.5 text-green-400">
            <span class="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse inline-block"></span>
            Live data
          </span>
          <span class="text-gray-700">·</span>
          <span class="text-violet-400">✦ AI-powered</span>
          <span class="text-gray-700">·</span>
          <span class="text-gray-500">vs. S&amp;P 500</span>
        </div>
        <h1 class="text-5xl sm:text-7xl md:text-8xl font-black text-white leading-none tracking-tight mb-3">
          Which finance YouTuber is<br>
          <span class="text-green-400">actually beating the stock market?</span>
        </h1>
        <div class="w-14 h-px bg-green-700 mx-auto my-8"></div>
        <p class="text-gray-400 text-xl leading-relaxed mb-4 max-w-2xl mx-auto">
          Every transcript analyzed. Every stock pick tracked. Every return measured against the S&amp;P 500. Deleted videos tracked. You see the truth.
        </p>
        <p class="text-gray-500 text-base mb-12 max-w-xl mx-auto">
          @if (pricesLastUpdated()) {
            Returns as of <span class="text-gray-300 font-medium">{{ pricesLastUpdated() }}</span> · Updated daily.
          } @else {
            <span class="text-gray-300 font-medium">Updated daily</span> for the most accurate results possible.
          }
        </p>
        <div class="grid grid-cols-2 md:grid-cols-3 gap-y-8 gap-x-0 mb-12 w-full max-w-3xl mx-auto">
          <div class="text-center px-8">
            <div class="text-5xl font-black text-white tabular-nums tracking-tight">{{ picksCount() | number }}</div>
            <div class="text-xs text-gray-500 uppercase tracking-widest mt-2 font-semibold">predictions analyzed</div>
          </div>
          <div class="text-center px-8">
            <div class="text-5xl font-black text-white tabular-nums tracking-tight">{{ youTubersCount() | number }}</div>
            <div class="text-xs text-gray-500 uppercase tracking-widest mt-2 font-semibold">YouTubers analyzed</div>
          </div>
          <div class="text-center px-8">
            <div class="text-5xl font-black text-white tabular-nums tracking-tight">{{ stocksCount() | number }}</div>
            <div class="text-xs text-gray-500 uppercase tracking-widest mt-2 font-semibold">stocks tracked</div>
          </div>
          <div class="text-center px-8">
            <div class="text-5xl font-black text-white tabular-nums tracking-tight">{{ currenciesCount() | number }}</div>
            <div class="text-xs text-gray-500 uppercase tracking-widest mt-2 font-semibold">currencies supported</div>
          </div>
          <div class="text-center px-8">
            <div class="text-5xl font-black text-white tabular-nums tracking-tight">{{ llmModelsCount() | number }}</div>
            <div class="text-xs text-gray-500 uppercase tracking-widest mt-2 font-semibold">cutting-edge LLM models used</div>
          </div>
          <div class="text-center px-8">
            <div class="text-5xl font-black tabular-nums tracking-tight text-white">{{ deletedVideosCount() | number }}</div>
            <div class="text-xs text-gray-500 uppercase tracking-widest mt-2 font-semibold">deleted videos detected 💀</div>
          </div>
        </div>
        <button (click)="scrollToLeaderboard()"
                class="inline-flex items-center gap-3 bg-green-800 hover:bg-green-700 text-white font-bold px-12 py-5 rounded-xl transition-colors text-lg">
          View Leaderboard
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
            <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7"/>
          </svg>
        </button>
      </div>
    </div>
    <div class="bg-gray-600 w-full">
    <div class="max-w-screen-2xl mx-auto px-4 md:px-6 py-10">

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

      @if ((auth.isAdmin || featureFlags.isEnabled('trending_stocks')) && trendingPicks().length > 0) {
        <div class="mb-6 bg-gray-900 border border-gray-700 rounded-xl overflow-hidden">
          <div class="flex flex-wrap items-center justify-between gap-2 px-4 md:px-6 py-4 md:py-5 border-b border-gray-700">
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-bold tracking-[0.18em] uppercase text-green-500">🔥 Trending today</span>
              <h2 class="text-lg md:text-2xl font-black text-white">Top stocks in the last 24hrs<span class="hidden md:inline"> &middot; By pick count</span></h2>
            </div>
          </div>
          <!-- desktop -->
          <table class="hidden md:table w-full">
            <thead class="bg-gray-800 text-gray-500 uppercase tracking-wider">
              <tr>
                <th class="px-6 py-2 text-left text-xs font-medium">#</th>
                <th class="px-6 py-2 text-left text-xs font-medium">Ticker</th>
                <th class="px-6 py-2 text-left text-xs font-medium">Company</th>
                <th class="px-6 py-2 text-right text-xs font-medium">Picks</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-800">
              @for (pick of trendingPicks(); track pick.tickerSymbol; let i = $index) {
                <tr class="hover:bg-gray-800/40">
                  <td class="px-6 py-4 text-gray-500 text-sm">{{ i + 1 }}</td>
                  <td class="px-6 py-4 font-semibold text-white text-sm">{{ pick.tickerSymbol }}</td>
                  <td class="px-6 py-4 text-white text-sm">{{ pick.companyName }}</td>
                  <td class="px-6 py-4 text-right text-white font-semibold text-sm">{{ pick.pickCount }}</td>
                </tr>
              }
            </tbody>
          </table>
          <!-- mobile -->
          <div class="md:hidden flex flex-col divide-y divide-gray-800">
            @for (pick of trendingPicks(); track pick.tickerSymbol; let i = $index) {
              <div class="flex items-center gap-3 px-4 py-3">
                <span class="text-gray-600 text-sm w-4">{{ i + 1 }}</span>
                <span class="font-semibold text-white text-sm">{{ pick.tickerSymbol }}</span>
                <span class="text-white text-sm flex-1 truncate">{{ pick.companyName }}</span>
                <span class="text-white font-semibold text-sm">{{ pick.pickCount }}</span>
              </div>
            }
          </div>
        </div>
      }

      <div id="leaderboard"></div>

      @if (auth.isAdmin || featureFlags.isEnabled('ibkr_banner')) {
      <a href="https://ibkr.com/referral/balazs702" target="_blank" rel="noopener sponsored"
         class="hidden md:flex items-center gap-3 mb-6 bg-gray-900 border border-gray-700 hover:border-green-800 rounded-xl px-5 py-4 transition-colors group">
        <div class="flex-shrink-0 bg-white rounded-lg p-1.5">
          <img src="https://www.google.com/s2/favicons?sz=64&domain=interactivebrokers.com" alt="Interactive Brokers" class="w-8 h-8">
        </div>
        <div class="flex-shrink-0 text-2xl font-black text-white">Interactive Brokers</div>
        <div class="flex-1 min-w-0 text-sm text-gray-400 flex items-center gap-1">
          <span class="truncate">Ready to act on what you discover? Get</span>
          <span class="relative group/tip inline-flex items-center gap-0.5 cursor-default flex-shrink-0">
            <span class="text-green-400">up to $1,000 bonus</span>
            <svg class="w-3 h-3 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
            <span class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-72 bg-gray-800 border border-gray-700 text-gray-300 text-xs leading-relaxed rounded-lg px-3 py-2.5 hidden group-hover/tip:block pointer-events-none z-20 shadow-xl font-normal whitespace-normal">
              $1 in IBKR Class A stock for every $300 deposited in the first year.
            </span>
          </span>
          <span class="flex-shrink-0">on deposits.</span>
        </div>
        <div class="flex-shrink-0 bg-green-800 group-hover:bg-green-700 text-white text-xs font-bold px-4 py-2 rounded-lg transition-colors whitespace-nowrap wiggle">Open account →</div>
      </a>
      <a href="https://ibkr.com/referral/balazs702" target="_blank" rel="noopener sponsored"
         class="flex md:hidden flex-col gap-3 mb-6 bg-gray-900 border border-gray-700 hover:border-green-800 rounded-xl px-5 py-4 transition-colors group">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="flex-shrink-0 bg-white rounded-lg p-1.5">
              <img src="https://www.google.com/s2/favicons?sz=64&domain=interactivebrokers.com" alt="Interactive Brokers" class="w-8 h-8">
            </div>
            <div class="text-2xl font-black text-white">Interactive Brokers</div>
          </div>
          <div class="flex-shrink-0 bg-green-800 group-hover:bg-green-700 text-white text-xs font-bold px-4 py-2 rounded-lg transition-colors whitespace-nowrap wiggle">Open account →</div>
        </div>
        <div class="text-sm font-semibold text-white">Get <span class="text-green-400">up to $1,000 bonus</span> on deposits.</div>
      </a>
      }

      @if (!loading() && !error()) {
        <div id="leaderboard" class="bg-gray-900 rounded-xl shadow-sm border border-gray-700 overflow-hidden">
          <div class="flex flex-wrap items-center justify-between gap-2 px-4 md:px-6 py-4 md:py-5 border-b border-gray-700">
            <div class="flex flex-col gap-0.5">
              <span class="text-sm font-bold tracking-[0.18em] uppercase text-green-500">★ Leaderboard</span>
              <h2 class="text-lg md:text-2xl font-black text-white">Finance YouTubers<span class="hidden md:inline"> &middot; Ranked by Alpha</span></h2>
            </div>
            <div class="flex items-center gap-1 p-1 bg-gray-800 rounded-lg">
              <button (click)="selectedTimeframe.set('1m')"
                      class="px-2 py-1.5 md:px-4 text-xs font-bold rounded-md transition-colors"
                      [class]="selectedTimeframe() === '1m' ? 'bg-green-800 text-white shadow-sm' : 'text-gray-400 hover:text-gray-300'">
                1M
              </button>
              <button (click)="selectedTimeframe.set('1y')"
                      class="px-2 py-1.5 md:px-4 text-xs font-bold rounded-md transition-colors"
                      [class]="selectedTimeframe() === '1y' ? 'bg-green-800 text-white shadow-sm' : 'text-gray-400 hover:text-gray-300'">
                1Y
              </button>
              <button (click)="selectedTimeframe.set('3y')"
                      class="px-2 py-1.5 md:px-4 text-xs font-bold rounded-md transition-colors"
                      [class]="selectedTimeframe() === '3y' ? 'bg-green-800 text-white shadow-sm' : 'text-gray-400 hover:text-gray-300'">
                3Y
              </button>
            </div>
          </div>
          <div class="md:hidden divide-y divide-gray-700">
            <div class="flex items-center gap-3 px-4 py-2 bg-gray-800 text-xs font-semibold text-gray-500 uppercase tracking-wider">
              <div class="w-7 flex-shrink-0">#</div>
              <div class="w-9 flex-shrink-0"></div>
              <div class="flex-1">Channel</div>
              <div class="flex-shrink-0">{{ timeframeLabel() }} Alpha</div>
            </div>
            @if (processedChannels().length === 0) {
              <div class="px-4 py-16 text-center text-gray-400 text-sm">No fully processed channels yet.</div>
            } @else {
              @for (row of visibleRows(); track row.handle; let i = $index) {
                <a
                  [routerLink]="['/channel', channelSlug(row.channelName)]"
                  class="flex items-center gap-3 px-4 py-3 hover:bg-gray-800/60 transition-colors"
                >
                  <div class="w-7 text-center flex-shrink-0">
                    @if (i === 0) { <span class="text-lg">🥇</span> }
                    @else if (i === 1) { <span class="text-lg">🥈</span> }
                    @else if (i === 2) { <span class="text-lg">🥉</span> }
                    @else { <span class="text-sm text-gray-400 font-mono">{{ i + 1 }}</span> }
                  </div>
                  <div class="relative flex-shrink-0">
                    @if (row.hasThumbnail) {
                      <img [src]="'/api/channels/' + row.handle + '/thumbnail'" [alt]="row.channelName"
                           class="w-9 h-9 rounded-full object-cover ring-2 ring-gray-700" />
                    } @else {
                      <div class="w-9 h-9 rounded-full bg-gray-700"></div>
                    }
                    @if (i === 0) {
                      <span class="absolute -top-3 left-1/2 -translate-x-1/2 text-base leading-none select-none">👑</span>
                    }
                  </div>
                  <span class="flex-1 font-semibold text-white truncate text-sm">{{ row.channelName }}</span>
                  <app-deleted-count [count]="row.archivarixDeletedCount" size="sm" class="flex-shrink-0" />
                  @if (scoreForRow(row) !== null) {
                    <span class="inline-flex items-center px-2 py-0.5 rounded-lg font-black font-mono text-xs flex-shrink-0"
                          [class]="scoreForRow(row)! >= 0 ? 'bg-green-900/70 text-green-400' : 'bg-red-900/70 text-red-400'">
                      {{ formatScore(scoreForRow(row)!) }}
                    </span>
                  } @else {
                    <span class="text-gray-400 flex-shrink-0 text-sm">—</span>
                  }
                </a>
              }
              @if (!auth.isAdmin && !auth.isAuthenticated && sortedRows().length > 5) {
                <div class="px-4 py-3 text-center bg-gray-800 border-t border-gray-700">
                  <button (click)="loginToShowAll()" class="px-4 py-1.5 text-xs font-semibold rounded-lg bg-green-800 text-white hover:bg-green-700 transition-colors">
                    ↓ Show {{ sortedRows().length - 5 }} more ↓
                  </button>
                </div>
              }
            }
          </div>
          <div class="hidden md:block">
          <table class="w-full">
            <thead>
              <tr class="border-b border-gray-700 text-left text-xs font-semibold text-gray-500 uppercase tracking-wider">
                <th class="px-4 py-4 w-8">#</th>
                <th class="px-3 py-4 text-right w-28">
                  <span class="inline-flex items-center gap-1 justify-end">{{ timeframeLabel() }} Alpha
                    <span class="relative group/tip cursor-default text-gray-300 hover:text-gray-500 normal-case tracking-normal font-normal">ⓘ
                      <span class="pointer-events-none absolute top-full left-0 mt-2 px-2 py-1.5 text-xs text-white bg-gray-800 rounded w-56 whitespace-normal opacity-0 group-hover/tip:opacity-100 transition-opacity z-10">
                        Average excess return vs. S&amp;P 500 over the selected timeframe. Based on explicit BUY picks, equally weighted.
                      </span>
                    </span>
                  </span>
                </th>
                <th class="px-6 py-4">Channel</th>
                <th class="px-6 py-4 text-right">Subscribers</th>
                <th class="px-6 py-4 text-right">Videos</th>
                <th class="px-6 py-4 text-right">
                  <span class="relative group/tip cursor-default">
                    # Deleted <span class="text-base">💀</span>
                    <span class="pointer-events-none absolute top-full right-0 mt-2 px-2 py-1.5 text-xs text-white bg-gray-800 rounded w-64 whitespace-normal opacity-0 group-hover/tip:opacity-100 transition-opacity z-10 normal-case tracking-normal font-normal">
                      Deleted videos not reflected in rankings — high count may indicate cherry-picked results.
                    </span>
                  </span>
                </th>
                @if (auth.isAdmin) {
                  <th class="px-6 py-4">Actions</th>
                }
              </tr>
            </thead>
            <tbody>
              @if (processedChannels().length === 0) {
                <tr>
                  <td [attr.colspan]="auth.isAdmin ? 7 : 6" class="px-6 py-16 text-center text-gray-400 text-sm">
                    No fully processed channels yet.
                  </td>
                </tr>
              } @else {
                @for (row of visibleRows(); track row.handle; let i = $index) {
                  <tr class="border-b border-gray-700 hover:bg-gray-800/60 transition-colors">
                    <td class="px-4 py-4 font-mono text-sm w-8 text-center">
                      @if (i === 0) { <span class="text-xl leading-none">🥇</span> }
                      @else if (i === 1) { <span class="text-xl leading-none">🥈</span> }
                      @else if (i === 2) { <span class="text-xl leading-none">🥉</span> }
                      @else { <span class="text-gray-400">{{ i + 1 }}</span> }
                    </td>
                    <td class="px-3 py-4 text-right w-28"
                        [title]="scoreForRow(row) !== null ? (eligibleForRow(row) + ' picks, ' + unresolvedForRow(row) + ' unresolved') : ''">
                      @if (scoreForRow(row) !== null) {
                        <span class="inline-flex items-center px-2.5 py-1 rounded-lg font-black font-mono text-sm"
                              [class]="scoreForRow(row)! >= 0 ? 'bg-green-900/70 text-green-400' : 'bg-red-900/70 text-red-400'">
                          {{ formatScore(scoreForRow(row)!) }}
                        </span>
                      } @else {
                        <span class="text-gray-400">—</span>
                      }
                    </td>
                    <td class="px-6 py-4">
                      <div class="flex items-center gap-2">
                        <a
                          [routerLink]="['/channel', channelSlug(row.channelName)]"
                          class="flex items-center gap-3 group"
                        >
                          <div class="relative flex-shrink-0">
                            @if (row.hasThumbnail) {
                              <img
                                [src]="'/api/channels/' + row.handle + '/thumbnail'"
                                [alt]="row.channelName"
                                class="w-9 h-9 rounded-full object-cover ring-2 ring-gray-700"
                              />
                            } @else {
                              <div class="w-9 h-9 rounded-full bg-gray-700"></div>
                            }
                            @if (i === 0) {
                              <span class="absolute -top-4 left-1/2 -translate-x-1/2 text-xl leading-none select-none">👑</span>
                            }
                          </div>
                          <span class="font-semibold text-white group-hover:text-green-400 transition-colors">
                            {{ row.channelName }}
                          </span>
                        </a>
                      </div>
                    </td>
                    <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {{ row.subscriberCount != null ? formatSubscriberCount(row.subscriberCount) : '—' }}
                    </td>
                    <td class="px-6 py-4 text-right text-gray-500 font-mono text-sm">
                      {{ row.totalVideos }}
                    </td>
                    <td class="px-6 py-4 text-right font-mono text-sm">
                      <app-deleted-count [count]="row.archivarixDeletedCount" size="sm" />
                    </td>
                    @if (auth.isAdmin) {
                    <td class="px-6 py-4">
                      <div class="flex items-center gap-2">
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
                      </div>
                    </td>
                    }
                  </tr>
                }
                @if (!auth.isAdmin && !auth.isAuthenticated && sortedRows().length > 5) {
                  <tr>
                    <td [attr.colspan]="6" class="px-6 py-3 text-center bg-gray-800 border-t border-gray-700">
                      <button (click)="loginToShowAll()" class="px-4 py-1.5 text-xs font-semibold rounded-lg bg-green-800 text-white hover:bg-green-700 transition-colors">
                        ↓ Show {{ sortedRows().length - 5 }} more ↓
                      </button>
                    </td>
                  </tr>
                }
              }
            </tbody>
          </table>
          </div>
        </div>

      }

      @if (!error()) {
        @if (auth.isAuthenticated && !auth.isAdmin && myChannelSuggestions().length > 0) {
          <div class="mt-10">
            <h2 class="text-xs font-semibold text-gray-400 uppercase tracking-wider mb-4">My Channel Suggestions</h2>
            <div class="bg-gray-900 rounded-xl shadow-sm border border-gray-700 overflow-x-auto">
              <table class="w-full min-w-max text-xs">
                <thead class="bg-gray-800 text-gray-500 uppercase tracking-wider">
                  <tr>
                    <th class="px-4 py-2 text-left font-medium">Channel</th>
                    <th class="px-4 py-2 text-left font-medium">Status</th>
                    <th class="px-4 py-2 text-left font-medium">Suggested</th>
                    <th class="px-4 py-2 text-left font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  @for (s of myChannelSuggestions(); track s.handle) {
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
                      <td class="px-4 py-2">
                        <span class="px-2 py-0.5 rounded-full text-xs font-semibold"
                              [class]="s.status === 'PENDING' ? 'bg-amber-100 text-amber-700' : s.status === 'ADDED' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'">
                          {{ s.status === 'PENDING' ? 'Pending' : s.status === 'ADDED' ? 'Added' : 'Rejected' }}
                        </span>
                      </td>
                      <td class="px-4 py-2 font-mono text-gray-500">{{ s.subscribedAt | date:'dd MMM yyyy' }}</td>
                      <td class="px-4 py-2">
                        <div class="flex items-center gap-2">
                          @if (s.status === 'PENDING') {
                          @if (togglingNotifyFor() === s.handle) {
                            <div class="animate-spin rounded-full h-5 w-5 border-2 border-amber-400 border-t-transparent"></div>
                          } @else if (s.notifyOnComplete) {
                            <button
                              (click)="toggleSuggestionNotify(s)"
                              class="relative group/tip text-amber-400 hover:text-amber-500 transition-colors"
                            >
                              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
                                <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/>
                              </svg>
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                                Unsubscribe from notification
                              </span>
                            </button>
                          } @else {
                            <button
                              (click)="toggleSuggestionNotify(s)"
                              class="relative group/tip text-gray-300 hover:text-amber-400 transition-colors"
                            >
                              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
                              </svg>
                              <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                                Notify me when added and processed
                              </span>
                            </button>
                          }
                          <button
                            (click)="deleteMyChannelSuggestion(s)"
                            class="relative group/tip text-gray-300 hover:text-danger-500 transition-colors"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                            <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity">
                              Remove suggestion
                            </span>
                          </button>
                          }
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

      }

      <div class="mx-4 mt-8 mb-2 text-center text-xs text-gray-300">
        Some data might be AI-generated and may contain inaccuracies. TubeReturns is not a registered investment advisor — this site is for entertainment purposes only, not financial advice.
      </div>

    </div>
    </div>
  `,
})
export class LeaderboardComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  readonly auth = inject(AuthService);
  private readonly recovery = inject(BackendRecoveryService);
  private readonly channelStore = inject(ChannelStoreService);
  readonly featureFlags = inject(FeatureFlagService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly rows = signal<Channel[]>([]);

  readonly processedChannels = computed(() =>
    this.rows().filter(r => r.discoveryComplete && r.totalVideos > 0 && r.processedVideos === r.totalVideos)
  );

  readonly selectedTimeframe = signal<'1m' | '1y' | '3y'>('1y');

  readonly sortedRows = computed(() => {
    const tf = this.selectedTimeframe();
    const scoreKey = tf === '1m' ? 'score1m' : tf === '1y' ? 'score1y' : 'score3y';
    return [...this.processedChannels()].sort((a, b) => {
      const aScore = a[scoreKey];
      const bScore = b[scoreKey];
      if (aScore === null && bScore === null) { return 0; }
      if (aScore === null) { return 1; }
      if (bScore === null) { return -1; }
      return bScore - aScore;
    });
  });

  readonly visibleRows = computed(() =>
    (this.auth.isAdmin || this.auth.isAuthenticated) ? this.sortedRows() : this.sortedRows().slice(0, 5)
  );

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly toast = signal<{ message: string; type: 'success' | 'error' } | null>(null);
  private toastTimer?: ReturnType<typeof setTimeout>;
  readonly confirmDialog = signal<{ message: string; destructive: boolean; onConfirm: () => void } | null>(null);
  readonly myChannelSuggestions = signal<MyChannelSuggestion[]>([]);
  readonly togglingNotifyFor = signal<string | null>(null);
  readonly picksCount = signal(0);
  readonly youTubersCount = signal(0);
  readonly stocksCount = signal(0);
  readonly currenciesCount = signal(0);
  readonly llmModelsCount = signal(0);
  readonly deletedVideosCount = signal(0);
  readonly pricesLastUpdated = signal<string | null>(null);
  readonly trendingPicks = signal<TrendingPick[]>([]);

  private suggestionRefreshSub?: Subscription;

  ngOnInit(): void {
    this.load();
    this.api.getStats().subscribe({
      next: (stats) => {
        setTimeout(() => {
          this.animateCount(stats.totalPicks, (v) => this.picksCount.set(v));
          this.animateCount(stats.totalChannels, (v) => this.youTubersCount.set(v));
          this.animateCount(stats.totalStocks, (v) => this.stocksCount.set(v));
          this.animateCount(stats.totalCurrencies, (v) => this.currenciesCount.set(v));
          this.animateCount(stats.totalLlmModels, (v) => this.llmModelsCount.set(v));
          if (stats.totalDeletedVideos) {
            this.animateCount(stats.totalDeletedVideos, (v) => this.deletedVideosCount.set(v));
          }
          if (stats.pricesLastUpdated) {
            this.pricesLastUpdated.set(new Date(stats.pricesLastUpdated).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }));
          }
        }, 300);
      },
      error: () => {},
    });
    this.api.getTrendingPicks().subscribe({ next: (picks) => this.trendingPicks.set(picks), error: () => {} });
    if (this.auth.isAuthenticated && !this.auth.isAdmin) {
      this.loadMyChannelSuggestions();
      this.suggestionRefreshSub = this.api.suggestionRefresh$.subscribe(() => this.loadMyChannelSuggestions());
    }
  }

  ngOnDestroy(): void {
    this.recovery.stopPolling();
    this.suggestionRefreshSub?.unsubscribe();
    clearTimeout(this.toastTimer);
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.recovery.stopPolling();

    this.api.getChannels().subscribe({
      next: (channels) => {
        this.rows.set(channels);
        this.channelStore.channels.set(channels);
        this.loading.set(false);
        if (this.route.snapshot.queryParamMap.get('scrollTo') === 'leaderboard') {
          setTimeout(() => this.scrollToLeaderboard(), 50);
        }
      },
      error: (_err: unknown) => {
        this.error.set('A deployment is probably in progress. Please try again shortly.');
        this.loading.set(false);
        this.recovery.startPolling(() => this.load());
      },
    });
  }

  formatSubscriberCount(count: number): string {
    if (count >= 1_000_000) { return `${(count / 1_000_000).toFixed(1)}M`; }
    if (count >= 1_000) { return `${Math.round(count / 1_000)}K`; }
    return count.toLocaleString();
  }

  loadMyChannelSuggestions(): void {
    this.api.getMyChannelSuggestions().subscribe({
      next: (suggestions) => this.myChannelSuggestions.set(suggestions),
      error: () => {},
    });
  }

  toggleSuggestionNotify(s: MyChannelSuggestion): void {
    if (this.togglingNotifyFor() !== null) { return; }
    this.togglingNotifyFor.set(s.handle);
    this.api.setChannelSuggestionNotify(s.handle, !s.notifyOnComplete).subscribe({
      next: () => {
        this.myChannelSuggestions.update((list) => list.map((item) => item.handle === s.handle ? { ...item, notifyOnComplete: !s.notifyOnComplete } : item));
        this.togglingNotifyFor.set(null);
      },
      error: () => this.togglingNotifyFor.set(null),
    });
  }

  deleteMyChannelSuggestion(s: MyChannelSuggestion): void {
    this.openConfirm(
      `Remove your suggestion for "${s.channelName}"?`,
      true,
      () => this.api.deleteMyChannelSuggestion(s.handle).subscribe({
        next: () => this.loadMyChannelSuggestions(),
        error: () => this.showToast(`Failed to remove suggestion for "${s.channelName}".`, 'error'),
      }),
    );
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

  private animateCount(target: number, setter: (v: number) => void, duration = 2200): void {
    const start = performance.now();
    const tick = (now: number) => {
      const progress = Math.min((now - start) / duration, 1);
      const eased = 1 - Math.pow(1 - progress, 3);
      setter(Math.round(eased * target));
      if (progress < 1) {
        requestAnimationFrame(tick);
      }
    };
    requestAnimationFrame(tick);
  }

  channelSlug(channelName: string): string {
    return channelName.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '') + '-returns';
  }

  loginToShowAll(): void {
    this.router.navigate(['/login'], { queryParams: { returnUrl: '/?scrollTo=leaderboard' } });
  }

  scrollToLeaderboard(): void {
    const el = document.getElementById('leaderboard');
    if (el) {
      const top = el.getBoundingClientRect().top + window.scrollY - 80;
      window.scrollTo({ top, behavior: 'smooth' });
    }
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

  scoreForRow(row: Channel): number | null {
    const tf = this.selectedTimeframe();
    return tf === '1m' ? row.score1m : tf === '1y' ? row.score1y : row.score3y;
  }

  eligibleForRow(row: Channel): number {
    const tf = this.selectedTimeframe();
    return tf === '1m' ? row.eligible1m : tf === '1y' ? row.eligible1y : row.eligible3y;
  }

  unresolvedForRow(row: Channel): number {
    const tf = this.selectedTimeframe();
    return tf === '1m' ? row.unresolved1m : tf === '1y' ? row.unresolved1y : row.unresolved3y;
  }

  timeframeLabel(): string {
    const tf = this.selectedTimeframe();
    return tf === '1m' ? '1M' : tf === '1y' ? '1Y' : '3Y';
  }



  formatScore(score: number): string {
    return (score >= 0 ? '+' : '') + score.toFixed(1) + '%';
  }

  hideImgOnError(event: Event): void {
    (event.target as HTMLImageElement).style.display = 'none';
  }
}
