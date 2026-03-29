import {
  Component,
  inject,
  signal,
  computed,
  AfterViewInit,
  OnDestroy,
  ViewChild,
  ElementRef,
  effect,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  CategoryScale,
  LinearScale,
  Tooltip,
  Filler,
} from 'chart.js';
import { ApiService } from '../../api/api.service';
import type { PricePoint } from '../../api/types';

Chart.register(LineController, LineElement, PointElement, CategoryScale, LinearScale, Tooltip, Filler);

type Timeframe = '1W' | '1M' | 'YTD' | '1Y' | '2Y' | '3Y' | '5Y' | '10Y';

const TIMEFRAMES: Timeframe[] = ['1W', '1M', 'YTD', '1Y', '2Y', '3Y', '5Y', '10Y'];

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

function toDate(): string {
  return new Date().toISOString().slice(0, 10);
}

function formatXLabel(dateStr: string, tf: Timeframe): string {
  const d = new Date(dateStr);
  if (tf === '1W' || tf === '1M') {
    return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }
  return d.toLocaleDateString('en-US', { year: 'numeric', month: 'short' });
}

function thinData(data: PricePoint[]): PricePoint[] {
  if (data.length <= 60) return data;
  const n = Math.ceil(data.length / 60);
  return data.filter((_, i) => i % n === 0);
}

@Component({
  selector: 'app-spy-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="bg-white rounded-xl shadow-sm border border-gray-200 p-6 mt-6">
      <div class="flex items-start justify-between mb-4 gap-4 flex-wrap">
        <div>
          <div class="flex items-baseline gap-3">
            <h2 class="text-base font-semibold text-gray-800">S&amp;P 500 (SPY)</h2>
            @if (!loading() && data().length > 0) {
              <span class="text-xl font-bold text-gray-900">\${{ lastClose().toFixed(2) }}</span>
              <span
                class="text-sm font-semibold"
                [class.text-primary-600]="change() >= 0"
                [class.text-danger-500]="change() < 0"
              >{{ change() >= 0 ? '+' : '' }}{{ change().toFixed(2) }}%</span>
            }
          </div>
          <p class="text-xs text-gray-400 mt-0.5">Historical closing prices</p>
        </div>
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
      </div>

      @if (loading()) {
        <div class="flex justify-center items-center h-56">
          <div class="animate-spin rounded-full h-7 w-7 border-2 border-primary-500 border-t-transparent"></div>
        </div>
      } @else if (data().length === 0) {
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
  private readonly api = inject(ApiService);

  @ViewChild('chartCanvas') private canvasRef?: ElementRef<HTMLCanvasElement>;

  readonly timeframes = TIMEFRAMES;
  readonly timeframe = signal<Timeframe>('1Y');
  readonly data = signal<PricePoint[]>([]);
  readonly loading = signal(true);

  readonly lastClose = computed(() => {
    const d = this.data();
    return d.length > 0 ? d[d.length - 1].close : 0;
  });

  readonly change = computed(() => {
    const d = this.data();
    if (d.length < 2) return 0;
    const first = d[0].close;
    const last = d[d.length - 1].close;
    return first > 0 ? ((last - first) / first) * 100 : 0;
  });

  private chart: Chart | null = null;
  private currentTf: Timeframe = '1Y';

  constructor() {
    effect(() => {
      const tf = this.timeframe();
      this.loadData(tf);
    });
  }

  ngAfterViewInit(): void {
    if (this.data().length > 0) {
      this.buildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  setTimeframe(tf: Timeframe): void {
    this.timeframe.set(tf);
  }

  private loadData(tf: Timeframe): void {
    this.currentTf = tf;
    this.loading.set(true);
    this.api.getStockPrices('SPY', fromDate(tf), toDate()).subscribe({
      next: (pts) => {
        this.data.set(pts);
        this.loading.set(false);
        setTimeout(() => this.rebuildChart(), 0);
      },
      error: () => {
        this.data.set([]);
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
    const raw = this.data();
    if (raw.length === 0) return;

    const ctx = this.canvasRef.nativeElement.getContext('2d');
    if (!ctx) return;

    const pts = thinData(raw);
    const tf = this.currentTf;
    const positive = this.change() >= 0;
    const color = positive ? '#2d7a2d' : '#cc1a1a';

    const gradient = ctx.createLinearGradient(0, 0, 0, 260);
    gradient.addColorStop(0.05, positive ? 'rgba(45,122,45,0.15)' : 'rgba(204,26,26,0.15)');
    gradient.addColorStop(0.95, 'rgba(0,0,0,0)');

    this.chart = new Chart(ctx, {
      type: 'line',
      data: {
        labels: pts.map((p) => p.date),
        datasets: [
          {
            label: 'SPY',
            data: pts.map((p) => p.close),
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
                const label = pts[idx]?.date ?? '';
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
}
