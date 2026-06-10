import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { Title, Meta } from '@angular/platform-browser';
import { ApiService } from '../../api/api.service';
import { AuthService } from '../../services/auth.service';
import { FeatureFlagService } from '../../services/feature-flag.service';
import { StockDetail, StockPickEntry } from '../../api/types';

@Component({
  selector: 'app-stock-detail',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen bg-gray-950 px-4 py-8 md:py-12">
      <div class="max-w-5xl mx-auto">
        @if (loading()) {
          <div class="flex justify-center py-24">
            <div class="animate-spin rounded-full h-8 w-8 border-2 border-primary-500 border-t-transparent"></div>
          </div>
        } @else if (error()) {
          <div class="text-center py-24">
            <h1 class="text-2xl font-black text-white mb-2">Stock not found</h1>
            <p class="text-sm text-gray-400">{{ error() }}</p>
            <a routerLink="/" class="mt-4 inline-block text-primary-600 hover:text-primary-700 text-sm font-medium">← Back to the leaderboard</a>
          </div>
        } @else if (stock(); as s) {
          <div class="mb-8">
            <a routerLink="/" [queryParams]="{ scrollTo: 'leaderboard' }" class="text-green-400 hover:text-green-300 text-sm font-medium mb-6 inline-block">
              ← Leaderboard
            </a>
            <div class="flex flex-wrap items-baseline gap-3 mt-2">
              <h1 class="text-2xl md:text-3xl font-black text-white">{{ s.companyName ?? s.tickerSymbol }}</h1>
              <span class="font-mono font-semibold text-green-400 text-lg">{{ s.tickerSymbol }}</span>
              @if (s.currency && s.currency !== 'USD') {
                <span class="text-xs text-gray-500 border border-gray-700 rounded px-1.5 py-0.5">{{ s.currency }}</span>
              }
            </div>
            <p class="text-sm text-gray-400 mt-2">
              {{ s.totalPicks }} BUY recommendation{{ s.totalPicks === 1 ? '' : 's' }} from
              {{ s.totalChannels }} channel{{ s.totalChannels === 1 ? '' : 's' }} — each pick measured from its video's publication date against the S&P 500.
            </p>
          </div>

          @if (auth.isAdmin || featureFlags.isEnabled('fastgraphs_banner')) {
          <a href="https://fastgraphs.com/?ref=balazs" target="_blank" rel="noopener sponsored"
             class="hidden md:flex items-center gap-3 mb-4 bg-gray-900 border border-gray-700 hover:border-green-800 rounded-xl px-5 py-4 transition-colors group">
            <div class="flex-shrink-0 bg-white rounded-lg p-1.5">
              <img src="/fastgraphs-logo-square.png" alt="FASTgraphs" class="w-8 h-8">
            </div>
            <div class="flex-shrink-0 text-2xl font-black text-white">FASTgraphs</div>
            <div class="flex-1 min-w-0 text-sm text-gray-400 truncate">Dig deeper into {{ s.tickerSymbol }} — <span class="text-green-400">25% off</span> with code <span class="font-mono text-blue-400 select-all cursor-text" (click)="$event.preventDefault(); $event.stopPropagation()">AFFILIATE25</span>.</div>
            <div class="flex-shrink-0 bg-green-800 group-hover:bg-green-700 text-white text-xs font-bold px-4 py-2 rounded-lg transition-colors whitespace-nowrap wiggle">Analyze →</div>
          </a>
          <a href="https://fastgraphs.com/?ref=balazs" target="_blank" rel="noopener sponsored"
             class="flex md:hidden flex-col gap-3 mb-4 bg-gray-900 border border-gray-700 hover:border-green-800 rounded-xl px-5 py-4 transition-colors group">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-3">
                <div class="flex-shrink-0 bg-white rounded-lg p-1.5">
                  <img src="/fastgraphs-logo-square.png" alt="FASTgraphs" class="w-8 h-8">
                </div>
                <div class="text-2xl font-black text-white">FASTgraphs</div>
              </div>
              <div class="flex-shrink-0 bg-green-800 group-hover:bg-green-700 text-white text-xs font-bold px-4 py-2 rounded-lg transition-colors whitespace-nowrap wiggle">Analyze →</div>
            </div>
            <div class="text-sm text-gray-400"><span class="text-green-400">25% off</span> with code <span class="font-mono text-blue-400 select-all cursor-text" (click)="$event.preventDefault(); $event.stopPropagation()">AFFILIATE25</span>.</div>
          </a>
          }
          <div class="bg-gray-900 rounded-xl shadow-sm border border-gray-700 overflow-hidden">
            @if (s.picks.length === 0) {
              <div class="p-12 text-center text-gray-400 text-sm">No picks recorded for this stock yet.</div>
            } @else {
              <div class="md:hidden divide-y divide-gray-700">
                @for (pick of s.picks; track pick.videoId + pick.channelSlug) {
                  <div class="px-4 py-3">
                    <div class="flex items-start justify-between gap-2 mb-1">
                      <a [routerLink]="['/channel', pick.channelSlug]" class="text-sm font-semibold text-white hover:text-green-400 transition-colors truncate">{{ pick.channelName }}</a>
                      <span class="text-xs text-gray-500 flex-shrink-0">{{ pick.videoPublishedAt | date: 'shortDate' }}</span>
                    </div>
                    @if (pick.videoTitle) {
                      <span class="block text-xs text-gray-400 truncate mb-2">{{ pick.videoTitle }}</span>
                    }
                    <div class="flex flex-wrap gap-2">
                      @for (col of timeColumns; track col) {
                        @if (alphaFor(pick, col) !== null) {
                          <span class="inline-flex items-center px-2 py-0.5 rounded font-black font-mono text-xs"
                                [class]="alphaFor(pick, col)! >= 0 ? 'bg-green-900/70 text-green-400' : 'bg-red-900/70 text-red-400'">
                            {{ col.toUpperCase() }} {{ formatPct(alphaFor(pick, col)) }}
                          </span>
                        } @else {
                          <span class="text-xs text-gray-500">{{ col.toUpperCase() }} —</span>
                        }
                      }
                    </div>
                  </div>
                }
              </div>
              <div class="hidden md:block">
                <table class="w-full table-fixed text-sm">
                  <thead>
                    <tr class="bg-gray-800 border-b border-gray-700 text-left text-xs text-gray-500 uppercase tracking-wider">
                      <th class="px-4 py-3 w-24">Date</th>
                      <th class="px-4 py-3 w-44">Channel</th>
                      <th class="px-4 py-3">Video</th>
                      <th class="px-4 py-3 w-28 text-right">1M Alpha</th>
                      <th class="px-4 py-3 w-28 text-right">1Y Alpha</th>
                      <th class="px-4 py-3 w-28 text-right">3Y Alpha</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (pick of s.picks; track pick.videoId + pick.channelSlug) {
                      <tr class="border-b border-gray-700 hover:bg-gray-800/60">
                        <td class="px-4 py-3 text-gray-500 whitespace-nowrap">{{ pick.videoPublishedAt | date: 'shortDate' }}</td>
                        <td class="px-4 py-3">
                          <div class="flex items-center gap-1 min-w-0">
                            <a [routerLink]="['/channel', pick.channelSlug]" class="text-white font-medium hover:text-green-400 transition-colors truncate">{{ pick.channelName }}</a>
                            @if (pick.approximatedPrices) {
                              <span class="relative group/approx inline-block flex-shrink-0 text-blue-400 font-normal cursor-default text-xl leading-none">~<span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1.5 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/approx:opacity-100 transition-opacity z-50">Return estimated using AI-approximated price data</span></span>
                            }
                          </div>
                        </td>
                        <td class="px-4 py-3">
                          @if (pick.videoTitle) {
                            <span class="text-gray-400 block truncate" [title]="pick.videoTitle">{{ pick.videoTitle }}</span>
                          } @else {
                            <span class="text-gray-600">—</span>
                          }
                        </td>
                        @for (col of timeColumns; track col) {
                          <td class="px-4 py-3 text-right whitespace-nowrap">
                            @if (alphaFor(pick, col) !== null) {
                              <span class="inline-flex items-center px-2.5 py-1 rounded-lg font-black font-mono text-sm"
                                    [class]="alphaFor(pick, col)! >= 0 ? 'bg-green-900/70 text-green-400' : 'bg-red-900/70 text-red-400'">
                                {{ formatPct(alphaFor(pick, col)) }}
                              </span>
                            } @else {
                              <span class="text-gray-600">—</span>
                            }
                          </td>
                        }
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>

          <p class="text-xs text-gray-600 mt-4">Alpha is the pick's return minus the S&P 500 return over the same period, starting from the video's publication date. Non-USD stocks are converted to USD using historical exchange rates.</p>
        }
      </div>
    </div>
  `,
})
export class StockDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly titleService = inject(Title);
  private readonly metaService = inject(Meta);
  readonly auth = inject(AuthService);
  readonly featureFlags = inject(FeatureFlagService);

  readonly timeColumns = ['1m', '1y', '3y'] as const;

  readonly stock = signal<StockDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const ticker = params.get('ticker');
      if (!ticker) { return; }
      this.load(ticker);
    });
  }

  ngOnDestroy(): void {
    this.resetPageMeta();
  }

  private load(ticker: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.getStock(ticker).subscribe({
      next: (stock) => {
        this.stock.set(stock);
        this.loading.set(false);
        this.setPageMeta(stock);
      },
      error: () => {
        this.error.set(`We don't track any picks for "${ticker.toUpperCase()}".`);
        this.loading.set(false);
      },
    });
  }

  alphaFor(pick: StockPickEntry, col: '1m' | '1y' | '3y'): number | null {
    return col === '1m' ? pick.alpha1m : col === '1y' ? pick.alpha1y : pick.alpha3y;
  }

  formatPct(value: number | null): string {
    if (value === null) { return '—'; }
    return (value >= 0 ? '+' : '') + value.toFixed(1) + '%';
  }

  private setPageMeta(stock: StockDetail): void {
    const name = stock.companyName ?? stock.tickerSymbol;
    const title = `${name} (${stock.tickerSymbol}) — YouTuber Stock Picks | TubeReturns`;
    const desc = `Which finance YouTubers recommended ${name}? ${stock.totalPicks} picks from ${stock.totalChannels} channels with real returns vs the S&P 500.`;
    const url = `https://tubereturns.com/stock/${stock.tickerSymbol}`;
    this.titleService.setTitle(title);
    this.metaService.updateTag({ name: 'description', content: desc });
    this.metaService.updateTag({ property: 'og:title', content: title });
    this.metaService.updateTag({ property: 'og:description', content: desc });
    this.metaService.updateTag({ property: 'og:url', content: url });
    this.metaService.updateTag({ name: 'twitter:title', content: title });
    this.metaService.updateTag({ name: 'twitter:description', content: desc });
  }

  private resetPageMeta(): void {
    const title = 'TubeReturns — Stock-Picking YouTubers Ranked by Returns';
    const desc = 'TubeReturns tracks and ranks finance YouTubers by their real stock-pick performance — 1-month, 1-year, and 3-year returns vs the S&P 500. See who actually beats the market.';
    this.titleService.setTitle(title);
    this.metaService.updateTag({ name: 'description', content: desc });
    this.metaService.updateTag({ property: 'og:title', content: title });
    this.metaService.updateTag({ property: 'og:description', content: desc });
    this.metaService.updateTag({ property: 'og:url', content: 'https://tubereturns.com/' });
    this.metaService.updateTag({ name: 'twitter:title', content: title });
    this.metaService.updateTag({ name: 'twitter:description', content: desc });
  }
}
