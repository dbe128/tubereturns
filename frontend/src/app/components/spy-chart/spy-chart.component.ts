import {
  Component,
  inject,
  signal,
  AfterViewInit,
  OnDestroy,
  ViewChild,
  ElementRef,
  Output,
  EventEmitter,
  Input,
} from '@angular/core';
import { forkJoin, of, Subject, Subscription } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Legend,
} from 'chart.js';
import { ApiService } from '../../api/api.service';
import type { PortfolioPricePoint, Channel } from '../../api/types';

Chart.register(LineController, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Legend);

type Timeframe = '1W' | '1M' | 'YTD' | '1Y' | '2Y' | '3Y' | '4Y' | '5Y' | '10Y';

const TIMEFRAMES: Timeframe[] = ['1W', '1M', 'YTD', '1Y', '2Y', '3Y', '4Y', '5Y', '10Y'];

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
    case '4Y':  now.setFullYear(now.getFullYear() - 4); break;
    case '5Y':  now.setFullYear(now.getFullYear() - 5); break;
    case '10Y': now.setFullYear(now.getFullYear() - 10); break;
  }
  return now.toISOString().slice(0, 10);
}

function maxPointsForTimeframe(tf: Timeframe): number {
  if (tf === '1W' || tf === '1M' || tf === 'YTD' || tf === '1Y') return Infinity;
  return 200;
}

function thinData(data: PortfolioPricePoint[], max: number): PortfolioPricePoint[] {
  if (!isFinite(max) || data.length <= max) return data;
  const n = Math.ceil(data.length / max);
  const result = data.filter((_, i) => i % n === 0);
  const last = data[data.length - 1];
  if (result[result.length - 1] !== last) {
    result.push(last);
  }
  return result;
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
  imports: [],
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mt-6">
      <div class="flex items-start justify-between mb-4 gap-4 flex-wrap">
        <div>
          <div class="flex items-center gap-2">
            <h2 class="text-base font-semibold text-gray-800">Channel Returns Comparison</h2>
            <button (click)="refresh.emit()" title="Refresh"
              class="p-1 text-gray-400 hover:text-gray-700 rounded hover:bg-gray-100 transition-colors text-base leading-none">↺</button>
          </div>
          <p class="text-xs text-gray-400 mt-0.5">Equal-weighted % return in USD since first pick vs S&amp;P 500</p>
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
          <div class="flex flex-wrap gap-2 justify-end">
            <button
              (click)="toggleSeries('SPY')"
              class="flex items-center gap-1.5 px-3 py-1 text-xs rounded-lg font-semibold border transition-colors"
              [class.opacity-40]="!isVisible('SPY')"
            >
              <span class="w-5 h-1 inline-block rounded-sm" style="background:#6b7280"></span>
              SPY
            </button>
            @for (p of channelList(); track p.handle) {
              <button
                (click)="toggleSeries(p.handle)"
                class="flex items-center gap-1.5 px-3 py-1 text-xs rounded-lg font-semibold border transition-colors"
                [class.opacity-40]="!isVisible(p.handle)"
              >
                <span class="w-5 h-1 inline-block rounded-sm" [style.background]="colorFor(p.handle)"></span>
                {{ p.channelName }}
              </button>
            }
          </div>
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
export class SpyChartComponent implements AfterViewInit, OnDestroy {
  @Input() set leaderboardTimeframe(tf: '1Y' | '3Y' | '5Y') {
    if (this.timeframe() !== tf) {
      this.timeframe.set(tf);
      this.visibleIds = new Set();
      this.trigger$.next({ channels: this.channelList(), tf });
    }
  }
  @Input() set channels(value: Channel[]) {
    this.channelList.set(value);
    this.visibleIds = new Set();
    this.trigger$.next({ channels: value, tf: this.timeframe() });
  }
  @Output() readonly refresh = new EventEmitter<void>();

  private readonly api = inject(ApiService);

  @ViewChild('chartCanvas') private canvasRef?: ElementRef<HTMLCanvasElement>;

  readonly timeframes = TIMEFRAMES;
  readonly timeframe = signal<Timeframe>('1Y');
  readonly spyData = signal<PortfolioPricePoint[]>([]);
  readonly loading = signal(true);
  readonly channelList = signal<Channel[]>([]);

  private chart: Chart | null = null;
  private allSeries: SeriesData[] = [];
  private visibleIds = new Set<string>();
  private colorMap = new Map<string, string>();

  private readonly trigger$ = new Subject<{ channels: Channel[]; tf: Timeframe }>();
  private readonly sub: Subscription = this.trigger$.pipe(
    switchMap(({ channels, tf }) => {
      this.loading.set(true);
      const tfFrom = fromDate(tf);
      channels.forEach((p, i) => this.colorMap.set(p.handle, PORTFOLIO_COLORS[i % PORTFOLIO_COLORS.length]));
      const requests = [
        this.api.getPortfolioPrices('SPY', tfFrom).pipe(catchError(() => of<PortfolioPricePoint[]>([]))),
        ...channels.map((p) =>
          this.api.getPortfolioPrices(p.handle, tfFrom).pipe(catchError(() => of<PortfolioPricePoint[]>([]))),
        ),
      ];
      return forkJoin(requests).pipe(map((results) => ({ results, channels })));
    }),
  ).subscribe({
    next: ({ results, channels }) => {
      const spyPts = results[0];
      this.spyData.set(spyPts);
      this.allSeries = [
        { id: 'SPY', label: 'SPY', points: spyPts, color: '#6b7280' },
        ...channels.map((p, i) => ({
          id: p.handle,
          label: p.channelName,
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

  ngAfterViewInit(): void {
    if (this.spyData().length > 0) {
      this.buildChart();
    }
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
    this.chart?.destroy();
  }

  setTimeframe(tf: Timeframe): void {
    this.timeframe.set(tf);
    this.visibleIds = new Set();
    this.trigger$.next({ channels: this.channelList(), tf });
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
    this.buildComparisonChart(ctx, visible);
  }

  private buildComparisonChart(ctx: CanvasRenderingContext2D, visible: SeriesData[]): void {
    const allDates = [...new Set(visible.flatMap((s) => s.points.map((p) => p.date)))].sort();
    const max = maxPointsForTimeframe(this.timeframe());
    const thinned = thinData(allDates.map((d) => ({ date: d } as PortfolioPricePoint)), max).map((p) => p.date);

    const allVals = visible.flatMap((s) =>
      s.points.map((p) => p.changePercent).filter((v): v is number => v != null && isFinite(v))
    );
    const dataMin = allVals.length ? Math.min(...allVals) : -10;
    const dataMax = allVals.length ? Math.max(...allVals) : 10;
    const pad = Math.max((dataMax - dataMin) * 0.1, 2);
    const rawMin = dataMin - pad;
    const rawMax = dataMax + pad;
    const range = rawMax - rawMin;
    const step = range < 20 ? 5 : range < 50 ? 10 : range < 150 ? 20 : range < 400 ? 50 : 100;
    const yMin = Math.floor(rawMin / step) * step;
    const yMax = Math.ceil(rawMax / step) * step;

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
            yAxisID: 'y',
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
            border: { display: true, color: '#000' },
          },
          y: {
            min: yMin,
            max: yMax,
            ticks: {
              font: { size: 11 },
              color: '#9ca3af',
              callback: (v) => `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(0)}%`,
            },
            grid: { color: '#cbd5e1' },
            border: { display: true, color: '#000' },
          },
          yRight: {
            position: 'right',
            min: yMin,
            max: yMax,
            ticks: {
              font: { size: 11 },
              color: '#9ca3af',
              callback: (v) => `${Number(v) > 0 ? '+' : ''}${Number(v).toFixed(0)}%`,
            },
            grid: { drawOnChartArea: false },
            border: { display: true, color: '#000' },
          },
        },
      },
    });
  }
}
