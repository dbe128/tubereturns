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
import { forkJoin } from 'rxjs';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';
import { BackendRecoveryService } from '../../services/backend-recovery.service';
import type { Channel, VideoSummary, PickPerformance } from '../../api/types';

type SortKey = 'index' | 'publishedAt' | 'viewCount' | 'transcriptStatus' | 'extractionStatus';
type PickSortKey = 'date' | 'company' | 'ticker' | '1m' | '1y' | '3y';
type SortDir = 'asc' | 'desc';

const TRANSCRIPT_ORDER: Record<VideoSummary['transcriptStatus'], number> = {
  DOWNLOADED: 0, NO_TRANSCRIPT: 1, PENDING: 2, DOWNLOADING: 3, FAILED: 4,
};

const EXTRACTION_ORDER: Record<VideoSummary['extractionStatus'], number> = {
  EXTRACTED: 0, EXTRACTING: 1, PENDING: 2, FAILED: 3,
};

const TRANSCRIPT_LABELS: Record<VideoSummary['transcriptStatus'], string> = {
  DOWNLOADING: 'Downloading',
  DOWNLOADED: 'Downloaded',
  NO_TRANSCRIPT: 'No transcript',
  FAILED: 'Failed',
  PENDING: 'Pending',
};

const EXTRACTION_LABELS: Record<VideoSummary['extractionStatus'], string> = {
  EXTRACTED: 'Extracted',
  EXTRACTING: 'Extracting',
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

const EXTRACTION_STYLES: Record<VideoSummary['extractionStatus'], string> = {
  EXTRACTED: 'bg-primary-50 text-primary-700',
  EXTRACTING: 'bg-blue-50 text-blue-600',
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
              <span class="text-gray-400 text-xs mt-0.5 inline-block">youtube.com/@{{ channel()!.handle }}</span>
            </div>
            <div class="flex items-center gap-6 flex-shrink-0">
              <div class="flex gap-8 text-sm text-gray-400">
                <div class="text-center">
                  <div class="text-2xl font-bold font-mono" [class]="pickReturnClass(channel()!.score1m)">{{ formatPickReturn(channel()!.score1m) }}</div>
                  <div class="text-xs uppercase tracking-wide">1M Alpha</div>
                </div>
                <div class="text-center">
                  <div class="text-2xl font-bold font-mono" [class]="pickReturnClass(channel()!.score1y)">{{ formatPickReturn(channel()!.score1y) }}</div>
                  <div class="text-xs uppercase tracking-wide">1Y Alpha</div>
                </div>
                <div class="text-center">
                  <div class="text-2xl font-bold font-mono" [class]="pickReturnClass(channel()!.score3y)">{{ formatPickReturn(channel()!.score3y) }}</div>
                  <div class="text-xs uppercase tracking-wide">3Y Alpha</div>
                </div>
                <div class="w-px bg-gray-200 self-stretch mx-2"></div>
                <div class="text-center">
                  <div class="text-2xl font-bold text-gray-800">{{ channel()?.totalVideos ?? '—' }}</div>
                  <div class="text-xs uppercase tracking-wide">Videos</div>
                </div>
                @if (auth.isAdmin) {
                  <div class="text-center">
                    <div class="text-2xl font-bold text-primary-600">{{ channel()?.processedVideos ?? '—' }}</div>
                    <div class="text-xs uppercase tracking-wide">Processed</div>
                  </div>
                }
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

        <div class="flex border-b border-gray-200 mb-4">
          @if (auth.isAdmin) {
          <button
            (click)="switchTab('videos')"
            class="px-6 py-3 text-sm font-medium border-b-2 transition-colors -mb-px"
            [class.border-primary-600]="activeTab() === 'videos'"
            [class.text-primary-600]="activeTab() === 'videos'"
            [class.border-transparent]="activeTab() !== 'videos'"
            [class.text-gray-500]="activeTab() !== 'videos'"
          >Videos</button>
          }
          <button
            (click)="switchTab('picks')"
            class="px-6 py-3 text-sm font-medium border-b-2 transition-colors -mb-px"
            [class.border-primary-600]="activeTab() === 'picks'"
            [class.text-primary-600]="activeTab() === 'picks'"
            [class.border-transparent]="activeTab() !== 'picks'"
            [class.text-gray-500]="activeTab() !== 'picks'"
          >Picks</button>
        </div>

        @if (activeTab() === 'videos' && auth.isAdmin) {
        <div class="bg-white border border-gray-200 rounded-xl shadow-sm px-5 py-4 mb-4">
          <div class="flex items-center justify-between mb-3">
            <span class="text-xs font-semibold text-gray-400 uppercase tracking-wider">Filters</span>
            @if (filterTranscript() || filterProcessing() || filterPick() || !hideNoPicks()) {
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
                <option value="EXTRACTED">Extracted</option>
                <option value="EXTRACTING">Extracting</option>
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
                  [ngModel]="hideNoPicks()"
                  (ngModelChange)="hideNoPicks.set($event)"
                  class="rounded border-gray-300 text-primary-600 focus:ring-primary-300"
                />
                Hide processed without picks
              </label>
            </div>

            <div class="flex items-end pb-1">
              <label class="flex items-center gap-2 text-sm text-gray-500 cursor-pointer select-none">
                <input
                  type="checkbox"
                  [ngModel]="hideUnprocessed()"
                  (ngModelChange)="hideUnprocessed.set($event)"
                  class="rounded border-gray-300 text-primary-600 focus:ring-primary-300"
                />
                Hide unprocessed
              </label>
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
                <th class="px-4 py-3">Video</th>
                <th
                  class="px-4 py-3 w-28 cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('publishedAt')"
                >Upload Date<span class="ml-1" [class.text-primary-500]="sortKey() === 'publishedAt'" [class.text-gray-300]="sortKey() !== 'publishedAt'">{{ sortKey() === 'publishedAt' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                <th
                  class="px-4 py-3 w-16 text-right whitespace-nowrap cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('viewCount')"
                >Views<span class="ml-1" [class.text-primary-500]="sortKey() === 'viewCount'" [class.text-gray-300]="sortKey() !== 'viewCount'">{{ sortKey() === 'viewCount' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                @if (auth.isAdmin) {
                <th
                  class="px-4 py-3 w-32 cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('transcriptStatus')"
                >Transcript<span class="ml-1" [class.text-primary-500]="sortKey() === 'transcriptStatus'" [class.text-gray-300]="sortKey() !== 'transcriptStatus'">{{ sortKey() === 'transcriptStatus' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                <th
                  class="px-4 py-3 w-28 cursor-pointer select-none hover:text-primary-600 transition-colors"
                  (click)="toggleSort('extractionStatus')"
                >Picks<span class="ml-1" [class.text-primary-500]="sortKey() === 'extractionStatus'" [class.text-gray-300]="sortKey() !== 'extractionStatus'">{{ sortKey() === 'extractionStatus' ? (sortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                <th class="px-4 py-3 w-36 text-gray-500">Model</th>
                }
                <th class="px-4 py-3 w-28 text-primary-600 whitespace-nowrap">▲ Buy</th>
                @if (auth.isAdmin) {
                <th class="px-4 py-3 w-32 whitespace-nowrap">Excl. Reason</th>
                <th class="px-4 py-3 w-24">Actions</th>
                }
              </tr>
            </thead>
            <tbody>
              @if (sorted().length === 0) {
                <tr>
                  <td [attr.colspan]="auth.isAdmin ? 11 : 6" class="px-4 py-12 text-center text-gray-400">
                    No videos match the current filters.
                  </td>
                </tr>
              } @else {
                @for (item of sorted(); track item.v.videoId) {
                  <tr class="border-b border-gray-100" [class.hover:bg-gray-50]="!item.v.excluded" [class.bg-amber-50]="item.v.excluded" [class.hover:bg-amber-100]="item.v.excluded">
                    <td class="px-4 py-3 text-gray-400 font-mono">{{ item.originalIndex }}</td>
                    <td class="px-4 py-3 max-w-0">
                      <div class="relative group/title flex items-center gap-1.5">
                        <span class="text-gray-900 block truncate">{{ item.v.title }}</span>
                        <span class="pointer-events-none absolute bottom-full left-0 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded max-w-sm whitespace-normal opacity-0 group-hover/title:opacity-100 transition-opacity z-50">{{ item.v.title }}</span>
                        @if (item.v.transcriptStatus === 'PENDING' || item.v.transcriptStatus === 'DOWNLOADING' || (item.v.transcriptStatus === 'DOWNLOADED' && item.v.extractionStatus !== 'EXTRACTED')) {
                          <span class="relative group/tip flex-shrink-0">
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-amber-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                            </svg>
                            <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tip:opacity-100 transition-opacity z-50">
                              Processing...
                            </span>
                          </span>
                        }
                      </div>
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
                        [ngClass]="processingStyle(item.v.extractionStatus)"
                      >{{ processingLabel(item.v.extractionStatus) }}</span>
                    </td>
                    <td class="px-4 py-3 text-gray-400 font-mono text-xs">
                      @if (item.v.extractionModel) {
                        {{ item.v.extractionModel.replace('openrouter/', '') }}
                      } @else {
                        <span class="text-gray-200">—</span>
                      }
                    </td>
                    }
                    <td class="px-4 py-3 font-mono font-medium text-sm">
                      @if (item.v.buyPicks.length > 0) {
                        @for (ticker of item.v.buyPicks; track ticker; let last = $last) {
                          @if (!unknownTickers().has(ticker) || auth.isAdmin) {
                          <span class="inline-block whitespace-nowrap">
                            @if (unknownTickers().has(ticker)) {
                              <span class="relative group/tk inline-block text-gray-400 italic">{{ ticker }}<span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs font-normal text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tk:opacity-100 transition-opacity z-50">Unresolved ticker</span></span><span class="relative group/unk inline-block text-yellow-500 ml-0.5 font-normal cursor-default text-3xl leading-none">⚠<span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/unk:opacity-100 transition-opacity z-50">Unknown Stock</span></span>
                            } @else {
                              <span class="relative group/tk inline-block" [class.text-primary-600]="!item.v.excluded" [class.text-gray-400]="item.v.excluded" [class.line-through]="item.v.excluded">{{ ticker }}
                                @if (tickerMap()[ticker]) {
                                  <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs font-normal text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/tk:opacity-100 transition-opacity z-50">{{ tickerMap()[ticker] }}</span>
                                }
                              </span>
                            }@if (!last) {, }
                          </span>
                          }
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
                          @if (item.v.transcriptStatus === 'DOWNLOADED' && item.v.extractionStatus !== 'EXTRACTING') {
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
        }

        @if (activeTab() === 'picks') {
        @if (!picksLoading() && !picksError() && picks().length > 0) {
        <div class="bg-white border border-gray-200 rounded-xl shadow-sm px-5 py-3 mb-4 flex items-center gap-4">
          <div class="flex flex-col gap-1">
            <label class="text-xs text-gray-500">Ticker</label>
            <select
              [ngModel]="pickTickerFilter()"
              (ngModelChange)="pickTickerFilter.set($event)"
              class="text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-gray-50 text-gray-700 focus:outline-none focus:ring-2 focus:ring-primary-300"
            >
              <option value="">All tickers</option>
              @for (ticker of allPickTickers(); track ticker) {
                <option [value]="ticker">{{ ticker }}</option>
              }
            </select>
          </div>
          @if (pickTickerFilter()) {
            <div class="flex items-end pb-1">
              <button (click)="pickTickerFilter.set('')" class="text-xs text-primary-600 hover:text-primary-800 font-medium">Clear</button>
            </div>
            <span class="text-xs text-gray-400 self-end pb-1.5">{{ filteredAndSortedPicks().length }} picks</span>
          }
        </div>
        }
        <div class="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
          @if (picksLoading()) {
            <div class="flex justify-center py-12">
              <div class="animate-spin rounded-full h-6 w-6 border-2 border-primary-500 border-t-transparent"></div>
            </div>
          } @else if (picksError()) {
            <div class="p-6 text-sm text-danger-500">{{ picksError() }}</div>
          } @else if (picks().length === 0) {
            <div class="p-12 text-center text-gray-400 text-sm">No picks extracted yet.</div>
          } @else {
            <table class="w-full text-sm">
              <thead>
                <tr class="bg-gray-50 border-b border-gray-200 text-left text-xs text-gray-500 uppercase tracking-wider">
                  <th class="px-4 py-3 w-28 cursor-pointer select-none hover:text-primary-600 transition-colors" (click)="togglePickSort('date')">Date<span class="ml-1" [class.text-primary-500]="pickSortKey() === 'date'" [class.text-gray-300]="pickSortKey() !== 'date'">{{ pickSortKey() === 'date' ? (pickSortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                  <th class="px-4 py-3 cursor-pointer select-none hover:text-primary-600 transition-colors" (click)="togglePickSort('company')">Company<span class="ml-1" [class.text-primary-500]="pickSortKey() === 'company'" [class.text-gray-300]="pickSortKey() !== 'company'">{{ pickSortKey() === 'company' ? (pickSortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                  <th class="px-4 py-3 w-28 cursor-pointer select-none hover:text-primary-600 transition-colors" (click)="togglePickSort('ticker')">Ticker<span class="ml-1" [class.text-primary-500]="pickSortKey() === 'ticker'" [class.text-gray-300]="pickSortKey() !== 'ticker'">{{ pickSortKey() === 'ticker' ? (pickSortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span></th>
                  <th class="px-4 py-3 w-36 text-right cursor-pointer select-none hover:text-primary-600 transition-colors" (click)="togglePickSort('1m')">
                    <span class="inline-flex items-center gap-1 justify-end">1M Alpha
                      <span class="relative group/tip cursor-default text-gray-300 hover:text-gray-500 normal-case tracking-normal font-normal" (click)="$event.stopPropagation()">ⓘ
                        <span class="pointer-events-none absolute top-full right-0 mt-2 px-2 py-1.5 text-xs text-white bg-gray-800 rounded w-48 whitespace-normal opacity-0 group-hover/tip:opacity-100 transition-opacity z-10">Pick return minus S&P 500 return over 1 month from the video date.</span>
                      </span>
                      <span class="ml-1" [class.text-primary-500]="pickSortKey() === '1m'" [class.text-gray-300]="pickSortKey() !== '1m'">{{ pickSortKey() === '1m' ? (pickSortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span>
                    </span>
                  </th>
                  <th class="px-4 py-3 w-32 text-right cursor-pointer select-none hover:text-primary-600 transition-colors" (click)="togglePickSort('1y')">
                    <span class="inline-flex items-center gap-1 justify-end">1Y Alpha
                      <span class="relative group/tip cursor-default text-gray-300 hover:text-gray-500 normal-case tracking-normal font-normal" (click)="$event.stopPropagation()">ⓘ
                        <span class="pointer-events-none absolute top-full right-0 mt-2 px-2 py-1.5 text-xs text-white bg-gray-800 rounded w-48 whitespace-normal opacity-0 group-hover/tip:opacity-100 transition-opacity z-10">Pick return minus S&P 500 return over 1 year from the video date.</span>
                      </span>
                      <span class="ml-1" [class.text-primary-500]="pickSortKey() === '1y'" [class.text-gray-300]="pickSortKey() !== '1y'">{{ pickSortKey() === '1y' ? (pickSortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span>
                    </span>
                  </th>
                  <th class="px-4 py-3 w-36 text-right cursor-pointer select-none hover:text-primary-600 transition-colors" (click)="togglePickSort('3y')">
                    <span class="inline-flex items-center gap-1 justify-end">3Y Alpha
                      <span class="relative group/tip cursor-default text-gray-300 hover:text-gray-500 normal-case tracking-normal font-normal" (click)="$event.stopPropagation()">ⓘ
                        <span class="pointer-events-none absolute top-full right-0 mt-2 px-2 py-1.5 text-xs text-white bg-gray-800 rounded w-48 whitespace-normal opacity-0 group-hover/tip:opacity-100 transition-opacity z-10">Pick return minus S&P 500 return over 3 years from the video date.</span>
                      </span>
                      <span class="ml-1" [class.text-primary-500]="pickSortKey() === '3y'" [class.text-gray-300]="pickSortKey() !== '3y'">{{ pickSortKey() === '3y' ? (pickSortDir() === 'asc' ? '↑' : '↓') : '↕' }}</span>
                    </span>
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (pick of filteredAndSortedPicks(); track pick.videoId + pick.tickerSymbol) {
                  @if (!pick.unknown || auth.isAdmin) {
                  <tr class="border-b border-gray-100 hover:bg-gray-50">
                    <td class="px-4 py-3 text-gray-500 whitespace-nowrap">
                      {{ pick.videoPublishedAt | date: 'shortDate' }}
                    </td>
                    <td class="px-4 py-3 max-w-0">
                      <span class="text-gray-900 block truncate">{{ pick.companyName ?? '—' }}</span>
                    </td>
                    <td class="px-4 py-3 font-mono font-medium">
                      <span class="inline-flex items-center gap-0.5 text-gray-800">{{ pick.tickerSymbol }}
                        @if (pick.unknown) {
                          <span class="relative group/unk inline-block text-yellow-500 font-normal cursor-default text-3xl leading-none">⚠<span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/unk:opacity-100 transition-opacity z-50">Unresolved ticker — excluded from returns</span></span>
                        }
                      </span>
                    </td>
                    @for (col of pickTimeColumns; track col) {
                      <td class="px-4 py-3 text-right font-mono text-xs whitespace-nowrap" [ngClass]="pickReturnClass(alphaForColumn(pick, col))">
                        {{ formatPickReturn(alphaForColumn(pick, col)) }}
                        @if (alphaForColumn(pick, col) !== null) {
                          <span class="relative group/spy inline-block ml-1 cursor-default not-italic text-base leading-none"
                                [class]="alphaForColumn(pick, col)! > 0 ? 'text-primary-600' : 'text-danger-500'">
                            {{ alphaForColumn(pick, col)! > 0 ? '↑' : '↓' }}
                            <span class="pointer-events-none absolute bottom-full right-0 mb-1.5 px-2 py-1 text-xs font-normal text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/spy:opacity-100 transition-opacity z-50">
                              Pick: {{ formatPickReturn(pickReturnForColumn(pick, col)) }} · S&P 500: {{ formatPickReturn(spyReturnForColumn(pick, col)) }}
                            </span>
                          </span>
                        }
                      </td>
                    }
                  </tr>
                  }
                }
              </tbody>
            </table>
          }
        </div>
        }
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

  readonly pickTimeColumns: ReadonlyArray<'1m' | '1y' | '3y'> = ['1m', '1y', '3y'];
  readonly tickerMap = signal<Record<string, string>>({});
  readonly unknownTickers = signal<ReadonlySet<string>>(new Set());
  readonly channel = signal<Channel | null>(null);
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
  readonly filterProcessing = signal<VideoSummary['extractionStatus'] | ''>('');
  readonly filterPick = signal('');
  readonly showExcluded = signal(false);
  readonly hideNoPicks = signal(true);
  readonly hideUnprocessed = signal(!this.auth.isAdmin);

  readonly transcriptPopup = signal<string | null>(null);
  readonly transcriptPopupPos = signal({ top: 0, left: 0, width: 520, maxHeight: 600 });

  readonly activeTab = signal<'videos' | 'picks'>('picks');
  readonly picks = signal<PickPerformance[]>([]);
  readonly picksLoading = signal(false);
  readonly picksError = signal<string | null>(null);
  readonly pickTickerFilter = signal('');
  readonly pickSortKey = signal<PickSortKey>('date');
  readonly pickSortDir = signal<SortDir>('asc');

  readonly allTickers = computed(() => {
    const tickers = new Set<string>();
    this.videos().forEach((v) => v.buyPicks.forEach((t) => tickers.add(t)));
    return Array.from(tickers).sort();
  });

  readonly allPickTickers = computed(() => {
    const tickers = new Set<string>();
    this.picks().filter(p => !p.unknown || this.auth.isAdmin).forEach(p => tickers.add(p.tickerSymbol));
    return Array.from(tickers).sort();
  });

  readonly filteredAndSortedPicks = computed(() => {
    const filter = this.pickTickerFilter();
    let list = filter ? this.picks().filter(p => p.tickerSymbol === filter) : this.picks();
    const key = this.pickSortKey();
    const dir = this.pickSortDir();
    list = [...list].sort((a, b) => {
      let cmp = 0;
      if (key === 'date') {
        cmp = new Date(a.videoPublishedAt).getTime() - new Date(b.videoPublishedAt).getTime();
      } else if (key === 'company') {
        cmp = (a.companyName ?? '').localeCompare(b.companyName ?? '');
      } else if (key === 'ticker') {
        cmp = a.tickerSymbol.localeCompare(b.tickerSymbol);
      } else {
        const colKey = key as '1m' | '1y' | '3y';
        const aVal = this.alphaForColumn(a, colKey);
        const bVal = this.alphaForColumn(b, colKey);
        if (aVal == null && bVal == null) { return 0; }
        if (aVal == null) { return 1; }
        if (bVal == null) { return -1; }
        cmp = aVal - bVal;
      }
      return dir === 'asc' ? cmp : -cmp;
    });
    return list;
  });

  readonly filtered = computed(() =>
    this.videos().filter((v) => {
      if (!this.showExcluded() && v.excluded) return false;
      if (this.hideUnprocessed() && (
        v.transcriptStatus === 'PENDING' ||
        v.transcriptStatus === 'DOWNLOADING' ||
        (v.transcriptStatus === 'DOWNLOADED' && v.extractionStatus !== 'EXTRACTED')
      )) return false;
      if (this.hideNoPicks() && v.extractionStatus === 'EXTRACTED' && v.buyPicks.length === 0) return false;
      if (this.filterTranscript() && v.transcriptStatus !== this.filterTranscript()) return false;
      if (this.filterProcessing() && v.extractionStatus !== this.filterProcessing()) return false;
      return !(this.filterPick() && !v.buyPicks.includes(this.filterPick()));

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
      } else if (key === 'extractionStatus') {
        cmp = EXTRACTION_ORDER[a.v.extractionStatus] - EXTRACTION_ORDER[b.v.extractionStatus];
      }
      return dir === 'asc' ? cmp : -cmp;
    });
    return withIndex;
  });

  ngOnInit(): void {
    this.api.getTickers().subscribe({ next: (d) => { this.tickerMap.set(d.companies); this.unknownTickers.set(new Set(d.unknownTickers)); }, error: () => {} });
    const channelId = this.route.snapshot.paramMap.get('channelId');
    if (channelId) {
      this.loadData(channelId);
      this.loadPicks();
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
      videos: this.api.getVideosForChannel(id),
    }).subscribe({
      next: ({ channel, videos }) => {
        this.channel.set(channel);
        this.videos.set(videos);
        this.loading.set(false);
      },
      error: (_err: unknown) => {
        this.error.set('A deployment is probably in progress. Please try again shortly.');
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

  switchTab(tab: 'videos' | 'picks'): void {
    this.activeTab.set(tab);
    if (tab === 'picks' && this.picks().length === 0 && !this.picksLoading()) {
      this.loadPicks();
    }
  }

  loadPicks(): void {
    const handle = this.route.snapshot.paramMap.get('channelId');
    if (!handle) return;
    this.picksLoading.set(true);
    this.picksError.set(null);
    this.api.getChannelPicks(handle).subscribe({
      next: (data) => { this.picks.set(data); this.picksLoading.set(false); },
      error: () => { this.picksError.set('Failed to load picks.'); this.picksLoading.set(false); },
    });
  }

  formatPickReturn(value: number | null | undefined): string {
    if (value == null) return '—';
    const sign = value >= 0 ? '+' : '';
    return `${sign}${value.toFixed(1)}%`;
  }

  pickReturnClass(value: number | null | undefined): string {
    if (value == null) return 'text-gray-300';
    return value >= 0 ? 'text-primary-600' : 'text-danger-500';
  }

  pickReturnForColumn(pick: PickPerformance, col: '1m' | '1y' | '3y'): number | null {
    if (col === '1m') { return pick.return1m; }
    if (col === '1y') { return pick.return1y; }
    return pick.return3y;
  }

  alphaForColumn(pick: PickPerformance, col: '1m' | '1y' | '3y'): number | null {
    if (col === '1m') { return pick.alpha1m; }
    if (col === '1y') { return pick.alpha1y; }
    return pick.alpha3y;
  }

  spyReturnForColumn(pick: PickPerformance, col: '1m' | '1y' | '3y'): number | null {
    const ret = this.pickReturnForColumn(pick, col);
    const alpha = this.alphaForColumn(pick, col);
    if (ret == null || alpha == null) { return null; }
    return ret - alpha;
  }

  toggleSort(key: SortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortKey.set(key);
      this.sortDir.set('asc');
    }
  }

  togglePickSort(key: PickSortKey): void {
    if (this.pickSortKey() === key) {
      this.pickSortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.pickSortKey.set(key);
      this.pickSortDir.set('asc');
    }
  }

  clearFilters(): void {
    this.filterTranscript.set('');
    this.filterProcessing.set('');
    this.filterPick.set('');
    this.showExcluded.set(false);
    this.hideNoPicks.set(true);
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

  processingLabel(status: VideoSummary['extractionStatus']): string {
    return EXTRACTION_LABELS[status];
  }

  transcriptStyle(status: VideoSummary['transcriptStatus']): string {
    return TRANSCRIPT_STYLES[status];
  }

  processingStyle(status: VideoSummary['extractionStatus']): string {
    return EXTRACTION_STYLES[status];
  }
}
