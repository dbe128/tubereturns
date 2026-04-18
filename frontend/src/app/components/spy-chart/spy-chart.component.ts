import {
  Component,
  inject,
  signal,
  computed,
  OnInit,
  AfterViewInit,
  OnDestroy,
  ViewChild,
  ElementRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Filler,
  Legend,
} from 'chart.js';
import { ApiService } from '../../api/api.service';
import type { PortfolioPricePoint, Portfolio } from '../../api/types';

Chart.register(LineController, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Filler, Legend);

type Timeframe = '1W' | '1M' | 'YTD' | '1Y' | '2Y' | '3Y' | '5Y' | '10Y';

const TIMEFRAMES: Timeframe[] = ['1W', '1M', 'YTD', '1Y', '2Y', '3Y', '5Y', '10Y'];

const PORTFOLIO_COLORS = ['#2563eb', '#16a34a', '#ea580c', '#7c3aed', '#db2777', '#0891b2'];

function fromDate(tf: Timeframe): string {
  const now = new Date();
  switch (tf) {
    case '1W':  now.setDate(now.getDate() - 7); break;
    case '1M':  now.setMonth(now.getMonth() - 1); break;
    case 'YTD': return `${new Date().getFullYear()}-01-01`;
    case '1Y':  now.setFullYear(now.getFullYear() - 1); break;
    case '2Y':  now.setFullYear(now.getFullYear() - 2); break;
    case '3Y':  now.setFullYear(now.getFullYear() - 3); break;
    case '5Y':  now.setFullYear(now.getFullYear() - 5); break;
    case '10Y': now.setFullYear(now.getFullYear() - 10); break;
  }
  return now.toISOString().slice(0, 10);
}

function formatXLabel(dateStr: string, tf: Timeframe): string {
  const d = new Date(dateStr);
  if (tf === '1W' || tf === '1M') {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short' });
}

function thinData(data: PortfolioPricePoint[]): PortfolioPricePoint[] {
  if (data.length <= 60) return data;
  const n = Math.ceil(data.length / 60);
  return data.filter((_, i) => i % n === 0);
}

interface SeriesData {
  id: string;
  label: string;
  points: PortfolioPricePoint[];
  color: string;
}

@Component({
  selector: 'app-spy-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mt-6">
      <div class="flex items-start justify-between mb-4 gap-4 flex-wrap">
        <div>
          @if (hasPortfolios()) {
            <h2 class="text-base font-semibold text-gray-800">Portfolio Comparison</h2>
            <p class="text-xs text-gray-400 mt-0.5">Equal-weighted % return since first pick vs S&amp;P 500</p>
          } @else {
            <div class="flex items-baseline gap-3">
              <h2 class="text-base font-semibold text-gray-800">S&amp;P 500 (SPY)</h2>
              @if (!loading() && spyData().length > 0) {
                <span class="text-xl font-bold text-gray-900">\${{ lastClose().toFixed(2) }}</span>
                <span
                  class="text-sm font-semibold"
                  [class.text-primary-600]="spyChange() >= 0"
                  [class.text-danger-500]="spyChange() < 0"
                >{{ spyChange() >= 0 ? '+' : '' }}{{ spyChange().toFixed(2) }}%</span>
              }
            </div>
            <p class="text-xs text-gray-400 mt-0.5">Historical closing prices</p>
          }
        </div>

        <div class="flex flex-col items-end gap-2">
          <div class="flex gap-1">
            @for (tf of timeframes; track tf) {
              <button
                (click)="setTimeframe(tf)"
                class="px-3 py-1 text-xs rounded-lg font-semibold transition-colors"
                [class.bg-primary-600]="timeframe() === tf"
                [class.text-white]="timeframe() === tf"
                [class.bg-gray-100]="timeframe() !== tf"
                [class.text-gray-500]="timeframe() !== tf"
                [class.hover:bg-gray-200]="timeframe() !== tf"
              >{{ tf }}</button>
            }
          </div>
          @if (hasPortfolios()) {
            <div class="flex flex-wrap gap-2 justify-end">
              <button
                (click)="toggleSeries('SPY')"
                class="flex items-center gap-1.5 px-3 py-1 text-xs rounded-lg font-semibold border transition-colors"
                [class.opacity-40]="!isVisible('SPY')"
              >
                <span class="w-3 h-0.5 inline-block rounded" style="background:#6b7280"></span>
                SPY
              </button>
              @for (p of portfolios(); track p.channelId) {
                <button
                  (click)="toggleSeries(p.channelId)"
                  class="flex items-center gap-1.5 px-3 py-1 text-xs rounded-lg font-semibold border transition-colors"
                  [class.opacity-40]="!isVisible(p.channelId)"
                >
                  <span class="w-3 h-0.5 inline-block rounded" [style.background]="colorFor(p.channelId)"></span>
                  {{ p.name }}
                </button>
              }
            </div>
          }
        </div>
      </div>

      @if (loading()) {
        <div class="flex justify-center items-center h-56">
          <div class="animate-spin rounded-full h-7 w-7 border-2 border-primary-500 border-t-transparent"></div>
        </div>
      } @else if (spyData().length === 0) {
        <div class="flex justify-center items-center h-56 text-gray-400 text-sm">
          No data available for this timeframe yet.
        </div>
      } @else {
        <div style="height: 260px; position: relative;">
          <canvas #chartCanvas></canvas>
        </div>
      }
    </div>
  `,
})
export class SpyChartComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(ApiService);

  @ViewChild('chartCanvas') private canvasRef?: ElementRef<HTMLCanvasElement>;

  readonly timeframes = TIMEFRAMES;
  readonly timeframe = signal<Timeframe>('1Y');
  readonly spyData = signal<PortfolioPricePoint[]>([]);
  readonly loading = signal(true);
  readonly portfolios = signal<Portfolio[]>([]);
  readonly hasPortfolios = computed(() => this.portfolios().length > 0);

  readonly lastClose = computed(() => {
    const d = this.spyData();
    const last = d.length > 0 ? d[d.length - 1].close : null;
    return last ?? 0;
  });

  readonly spyChange = computed(() => {
    const d = this.spyData();
    if (d.length === 0) return 0;
    return d[d.length - 1].changePercent;
  });

  private chart: Chart | null = null;
  private allSeries: SeriesData[] = [];
  private visibleIds = new Set<string>();
  private colorMap = new Map<string, string>();

  ngOnInit(): void {
    this.api.getPortfolios().pipe(catchError(() => of<Portfolio[]>([]))).subscribe((portfolios) => {
      this.portfolios.set(portfolios);
      if (portfolios.length > 0) {
        this.loadComparisonData(portfolios, this.timeframe());
      } else {
        this.loadSpyData(this.timeframe());
      }
    });
  }

  ngAfterViewInit(): void {
    if (this.spyData().length > 0) {
      this.buildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  setTimeframe(tf: Timeframe): void {
    this.timeframe.set(tf);
    if (this.hasPortfolios()) {
      this.loadComparisonData(this.portfolios(), tf);
    } else {
      this.loadSpyData(tf);
    }
  }

  toggleSeries(id: string): void {
    if (this.visibleIds.has(id)) {
      if (this.visibleIds.size > 1) {
        this.visibleIds.delete(id);
      }
    } else {
      this.visibleIds.add(id);
    }
    this.rebuildChart();
  }

  isVisible(id: string): boolean {
    return this.visibleIds.has(id);
  }

  colorFor(channelId: string): string {
    return this.colorMap.get(channelId) ?? '#6b7280';
  }

  private loadSpyData(tf: Timeframe): void {
    this.loading.set(true);
    this.api.getPortfolioPrices('SPY', fromDate(tf)).subscribe({
      next: (pts) => {
        this.spyData.set(pts);
        this.allSeries = [{ id: 'SPY', label: 'SPY', points: pts, color: '#6b7280' }];
        this.visibleIds = new Set(['SPY']);
        this.loading.set(false);
        setTimeout(() => this.rebuildChart(), 0);
      },
      error: () => {
        this.spyData.set([]);
        this.loading.set(false);
      },
    });
  }

  private loadComparisonData(portfolios: Portfolio[], tf: Timeframe): void {
    this.loading.set(true);

    const tfFrom = fromDate(tf);

    portfolios.forEach((p, i) => {
      this.colorMap.set(p.channelId, PORTFOLIO_COLORS[i % PORTFOLIO_COLORS.length]);
    });

    const requests = [
      this.api.getPortfolioPrices('SPY', tfFrom).pipe(catchError(() => of<PortfolioPricePoint[]>([]))),
      ...portfolios.map((p) =>
        this.api.getPortfolioPrices(p.channelId, tfFrom).pipe(catchError(() => of<PortfolioPricePoint[]>([]))),
      ),
    ];

    forkJoin(requests).subscribe({
      next: (results) => {
        const spyPts = results[0];
        this.spyData.set(spyPts);

        this.allSeries = [
          { id: 'SPY', label: 'SPY', points: spyPts, color: '#6b7280' },
          ...portfolios.map((p, i) => ({
            id: p.channelId,
            label: p.name,
            points: results[i + 1],
            color: PORTFOLIO_COLORS[i % PORTFOLIO_COLORS.length],
          })),
        ];

        if (this.visibleIds.size === 0) {
          this.visibleIds = new Set(this.allSeries.map((s) => s.id));
        }
        this.loading.set(false);
        setTimeout(() => this.rebuildChart(), 0);
      },
      error: () => {
        this.spyData.set([]);
        this.loading.set(false);
      },
    });
  }

  private rebuildChart(): void {
    this.chart?.destroy();
    this.chart = null;
    this.buildChart();
  }

  private buildChart(): void {
    if (!this.canvasRef) return;
    const ctx = this.canvasRef.nativeElement.getContext('2d');
    if (!ctx) return;

    const visible = this.allSeries.filter((s) => this.visibleIds.has(s.id));
    if (visible.length === 0) return;

    const spyOnly = visible.length === 1 && visible[0].id === 'SPY' && !this.hasPortfolios();

    if (spyOnly) {
      this.buildSpyChart(ctx, visible[0].points);
    } else {
      this.buildComparisonChart(ctx, visible);
    }
  }

  private buildSpyChart(ctx: CanvasRenderingContext2D, pts: PortfolioPricePoint[]): void {
    const thinned = thinData(pts);
    const tf = this.timeframe();
    const change = this.spyChange();
    const positive = change >= 0;
    const color = positive ? '#2d7a2d' : '#cc1a1a';

    const gradient = ctx.createLinearGradient(0, 0, 0, 260);
    gradient.addColorStop(0.05, positive ? 'rgba(45,122,45,0.15)' : 'rgba(204,26,26,0.15)');
    gradient.addColorStop(0.95, 'rgba(0,0,0,0)');

    this.chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: thinned.map((p) => p.date),
        datasets: [
          {
            label: 'SPY',
            data: thinned.map((p) => p.close ?? 0),
            borderColor: color,
            borderWidth: 2,
            pointRadius: 0,
            pointHoverRadius: 4,
            fill: true,
            backgroundColor: gradient,
            tension: 0,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (items) =>
                new Date(items[0].label).toLocaleDateString('en-US', {
                  year: 'numeric',
                  month: 'short',
                  day: 'numeric',
                }),
              label: (item) => `$${(item.parsed.y as number).toFixed(2)}`,
            },
            bodyFont: { size: 12 },
            cornerRadius: 8,
            borderColor: '#e5e7eb',
            borderWidth: 1,
          },
        },
        scales: {
          x: {
            ticks: {
              font: { size: 11 },
              color: '#9ca3af',
              maxTicksLimit: 8,
              callback: (_val, idx) => {
                const label = thinned[idx]?.date ?? '';
                return formatXLabel(label, tf);
              },
            },
            grid: { display: false },
            border: { display: false },
          },
          y: {
            ticks: {
              font: { size: 11 },
              color: '#9ca3af',
              callback: (v) => `$${Number(v).toFixed(0)}`,
            },
            grid: { color: '#f0f0f0' },
            border: { display: false },
          },
        },
      },
    });
  }

  private buildComparisonChart(ctx: CanvasRenderingContext2D, visible: SeriesData[]): void {
    const allDates = [...new Set(visible.flatMap((s) => s.points.map((p) => p.date)))].sort();
    const thinned = allDates.length > 60
      ? allDates.filter((_, i) => i % Math.ceil(allDates.length / 60) === 0)
      : allDates;

    this.chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: thinned,
        datasets: visible.map((s) => {
          const pointMap = new Map(s.points.map((p) => [p.date, p.changePercent]));
          return {
            label: s.label,
            data: thinned.map((d) => pointMap.get(d) ?? NaN),
            borderColor: s.color,
            borderWidth: 2,
            pointRadius: 0,
            pointHoverRadius: 4,
            fill: false,
            tension: 0,
            spanGaps: true,
          };
        }),
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: 'index', intersect: false },
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              title: (items) =>
                new Date(items[0].label).toLocaleDateString('en-US', {
                  year: 'numeric',
                  month: 'short',
                  day: 'numeric',
                }),
              label: (item) => {
                const v = item.parsed.y;
                if (v == null || !isFinite(v)) return '';
                return `${item.dataset.label}: ${v >= 0 ? '+' : ''}${v.toFixed(2)}%`;
              },
            },
            bodyFont: { size: 12 },
            cornerRadius: 8,
            borderColor: '#e5e7eb',
            borderWidth: 1,
          },
        },
        scales: {
          x: {
            ticks: {
              font: { size: 11 },
              color: '#9ca3af',
              maxTicksLimit: 8,
              callback: (_val, idx) => {
                const label = thinned[idx] ?? '';
                return new Date(label).toLocaleDateString('en-US', { year: 'numeric', month: 'short' });
              },
            },
            grid: { display: false },
            border: { display: false },
          },
          y: {
            ticks: {
              font: { size: 11 },
              color: '#9ca3af',
              callback: (v) => `${Number(v) >= 0 ? '+' : ''}${Number(v).toFixed(0)}%`,
            },
            grid: { color: '#f0f0f0' },
            border: { display: false },
          },
        },
      },
    });
  }
}
