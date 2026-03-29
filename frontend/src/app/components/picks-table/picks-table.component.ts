import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';
import type { Pick } from '../../api/types';
import { ReturnBadgeComponent } from '../return-badge/return-badge.component';

@Component({
  selector: 'app-picks-table',
  standalone: true,
  imports: [CommonModule, ReturnBadgeComponent],
  template: `
    @if (picks().length === 0) {
      <p class="text-gray-500 py-8 text-center">No picks found.</p>
    } @else {
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-200 text-left text-gray-500 text-xs uppercase tracking-wider">
              <th class="pb-2 pr-4">Ticker</th>
              <th class="pb-2 pr-4">Signal</th>
              <th class="pb-2 pr-4">Video</th>
              <th class="pb-2 pr-4 text-right">1d</th>
              <th class="pb-2 pr-4 text-right">7d</th>
              <th class="pb-2 pr-4 text-right">30d</th>
              <th class="pb-2 pr-4 text-right">90d</th>
              <th class="pb-2 text-right">1y</th>
            </tr>
          </thead>
          <tbody>
            @for (pick of picks(); track pick.id) {
              <tr class="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                <td class="py-3 pr-4">
                  <div class="font-mono font-bold text-gray-900">{{ pick.tickerSymbol }}</div>
                  @if (pick.companyName) {
                    <div class="text-gray-400 text-xs">{{ pick.companyName }}</div>
                  }
                </td>
                <td class="py-3 pr-4">
                  <span
                    class="inline-block px-2 py-0.5 rounded text-xs font-semibold"
                    [class.bg-success-50]="pick.signal === 'BUY'"
                    [class.text-success-600]="pick.signal === 'BUY'"
                    [class.bg-danger-50]="pick.signal === 'SELL'"
                    [class.text-danger-600]="pick.signal === 'SELL'"
                  >{{ pick.signal }}</span>
                </td>
                <td class="py-3 pr-4 max-w-xs">
                  <a
                    [href]="'https://www.youtube.com/watch?v=' + pick.videoId"
                    target="_blank"
                    rel="noreferrer"
                    class="text-primary-600 hover:underline truncate block"
                    [title]="pick.videoTitle ?? ''"
                  >{{ pick.videoTitle ?? pick.videoId }}</a>
                  <div class="text-gray-400 text-xs">
                    {{ pick.extractionTimestamp | date: 'shortDate' }}
                  </div>
                </td>
                <td class="py-3 pr-4 text-right">
                  <app-return-badge [value]="pick.performance?.return1d" />
                </td>
                <td class="py-3 pr-4 text-right">
                  <app-return-badge [value]="pick.performance?.return7d" />
                </td>
                <td class="py-3 pr-4 text-right">
                  <app-return-badge [value]="pick.performance?.return30d" />
                </td>
                <td class="py-3 pr-4 text-right">
                  <app-return-badge [value]="pick.performance?.return90d" />
                </td>
                <td class="py-3 text-right">
                  <app-return-badge [value]="pick.performance?.return1y" />
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class PicksTableComponent {
  readonly picks = input<Pick[]>([]);
}
