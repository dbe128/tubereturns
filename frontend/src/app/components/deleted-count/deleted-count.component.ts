import { Component, input } from '@angular/core';

@Component({
  selector: 'app-deleted-count',
  standalone: true,
  template: `
    @if (count() == null) {
      <span class="relative group/del inline-block cursor-default text-gray-600">?
        <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-2 py-1 text-xs text-white bg-gray-800 rounded whitespace-nowrap opacity-0 group-hover/del:opacity-100 transition-opacity z-10">
          Not yet scanned
        </span>
      </span>
    } @else if (count() === 0) {
      <span [class]="sizeClass() + ' text-green-400'">0</span>
    } @else if (count()! >= 10) {
      <span [class]="sizeClass() + ' text-red-400'">💀 {{ count() }}+</span>
    } @else {
      <span [class]="sizeClass() + ' text-amber-400'">💀 {{ count() }}+</span>
    }
  `,
})
export class DeletedCountComponent {
  readonly count = input<number | null | undefined>(null);
  readonly size = input<'sm' | 'lg'>('sm');

  sizeClass(): string {
    return this.size() === 'lg' ? 'text-2xl font-bold' : 'font-mono text-sm';
  }
}
