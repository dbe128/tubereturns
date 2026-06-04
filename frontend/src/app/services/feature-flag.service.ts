import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from '../api/api.service';
import type { FeatureFlag } from '../api/types';

@Injectable({ providedIn: 'root' })
export class FeatureFlagService {
  private readonly api = inject(ApiService);
  private readonly flags = signal<FeatureFlag[]>([]);

  load(): void {
    this.api.getFeatureFlags().subscribe({ next: (f) => this.flags.set(f), error: () => {} });
  }

  isEnabled(key: string): boolean {
    return this.flags().find(f => f.key === key)?.enabled ?? false;
  }

  getAll(): FeatureFlag[] {
    return this.flags();
  }

  set(key: string, enabled: boolean): void {
    this.api.setFeatureFlag(key, enabled).subscribe({
      next: (updated) => this.flags.update(flags => flags.map(f => f.key === updated.key ? updated : f)),
      error: () => {},
    });
  }
}
