import { inject, Injectable, OnDestroy } from '@angular/core';
import { ApiService } from '../api/api.service';
import { Subscription } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class BackendRecoveryService implements OnDestroy {
  private readonly api = inject(ApiService);
  private intervalId: ReturnType<typeof setInterval> | null = null;
  private subscription: Subscription | null = null;

  startPolling(onRecovered: () => void): void {
    this.stopPolling();
    this.intervalId = setInterval(() => {
      this.subscription = this.api.checkHealth().subscribe({
        next: () => {
          this.stopPolling();
          onRecovered();
        },
        error: () => {},
      });
    }, 5000);
  }

  stopPolling(): void {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    this.subscription?.unsubscribe();
    this.subscription = null;
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }
}
