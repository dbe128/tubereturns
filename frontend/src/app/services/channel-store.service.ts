import { Injectable, inject, signal } from '@angular/core';
import { ApiService } from '../api/api.service';
import type { Channel } from '../api/types';

@Injectable({ providedIn: 'root' })
export class ChannelStoreService {
  private readonly api = inject(ApiService);

  readonly channels = signal<Channel[]>([]);

  load(): void {
    this.api.getChannels().subscribe((channels: Channel[]) => this.channels.set(channels));
  }
}
