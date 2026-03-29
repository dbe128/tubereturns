import { Component, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-return-badge',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (value() == null) {
      <span class="text-gray-400 text-sm">—</span>
    } @else {
      <span
        class="inline-flex items-center gap-0.5 font-mono text-sm font-semibold"
        [class.text-success-600]="value()! >= 0"
        [class.text-danger-600]="value()! < 0"
      >
        {{ value()! >= 0 ? '+' : '' }}{{ (value()! * 100).toFixed(2) }}{{ suffix() }}
      </span>
    }
  `,
})
export class ReturnBadgeComponent {
  readonly value = input<number | null | undefined>(null);
  readonly suffix = input('%');
}
