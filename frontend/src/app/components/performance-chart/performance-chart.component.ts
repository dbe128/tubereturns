import {
  Component,
  input,
  OnChanges,
  AfterViewInit,
  ViewChild,
  ElementRef,
  OnDestroy,
} from '@angular/core';
import { Chart, BarController, BarElement, CategoryScale, LinearScale, Tooltip, GridLineOptions } from 'chart.js';
import type { Pick } from '../../api/types';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip);

@Component({
  selector: 'app-performance-chart',
  standalone: true,
  template: `
    @if (chartData().length === 0) {
      <p class="text-gray-400 text-sm text-center py-8">No performance data yet.</p>
    } @else {
      <div style="height: 240px; position: relative;">
        <canvas #chartCanvas></canvas>
      </div>
    }
  `,
})
export class PerformanceChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  readonly picks = input<Pick[]>([]);

  @ViewChild('chartCanvas') private canvasRef?: ElementRef<HTMLCanvasElement>;

  private chart: Chart | null = null;

  chartData(): { ticker: string; return30d: number; signal: string }[] {
    return this.picks()
      .filter((p) => p.performance?.return30d != null)
      .map((p) => ({
        ticker: p.tickerSymbol,
        return30d: parseFloat(((p.performance!.return30d ?? 0) * 100).toFixed(2)),
        signal: p.signal,
      }))
      .sort((a, b) => b.return30d - a.return30d)
      .slice(0, 15);
  }

  ngAfterViewInit(): void {
    this.buildChart();
  }

  ngOnChanges(): void {
    if (this.chart) {
      this.rebuildChart();
    }
  }

  ngOnDestroy(): void {
    this.chart?.destroy();
  }

  private rebuildChart(): void {
    this.chart?.destroy();
    this.chart = null;
    setTimeout(() => this.buildChart(), 0);
  }

  private buildChart(): void {
    if (!this.canvasRef) return;
    const data = this.chartData();
    if (data.length === 0) return;

    const ctx = this.canvasRef.nativeElement.getContext('2d');
    if (!ctx) return;

    this.chart = new Chart(ctx, {
      type: 'bar',
      data: {
        labels: data.map((d) => d.ticker),
        datasets: [
          {
            label: '30d Return',
            data: data.map((d) => d.return30d),
            backgroundColor: data.map((d) => (d.return30d >= 0 ? 'rgba(22,163,74,0.85)' : 'rgba(220,38,38,0.85)')),
            borderRadius: 3,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          tooltip: {
            callbacks: {
              label: (ctx) => `${ctx.parsed.y}%`,
            },
          },
          legend: { display: false },
        },
        scales: {
          x: {
            ticks: { font: { size: 11 } },
            grid: { color: '#f0f0f0' },
          },
          y: {
            ticks: {
              font: { size: 11 },
              callback: (v) => `${v}%`,
            },
            grid: { color: '#f0f0f0' },
          },
        },
      },
    });
  }
}
