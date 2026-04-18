import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { z } from 'zod';
import {
  ChannelSchema,
  ChannelStatsSchema,
  VideoSummarySchema,
  PickSchema,
  PricePointSchema,
  PortfolioPricePointSchema,
  PortfolioSchema,
} from './types';
import type { Channel, ChannelStats, VideoSummary, Pick, PricePoint, PortfolioPricePoint, Portfolio } from './types';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  private handleError(err: HttpErrorResponse): Observable<never> {
    if (err.status === 0) {
      return throwError(() => new Error('Cannot reach the backend. Make sure the server is running on port 8080.'));
    }
    if (err.status >= 500) {
      return throwError(() => new Error('The service is not available, please try again later.'));
    }
    return throwError(() => new Error(err.message));
  }

  private validated<T>(schema: z.ZodType<T>, source$: Observable<unknown>): Observable<T> {
    return source$.pipe(
      map((data) => schema.parse(data)),
      catchError((err: unknown) => {
        if (err instanceof HttpErrorResponse) {
          return this.handleError(err);
        }
        return throwError(() => err);
      }),
    );
  }

  getChannels(): Observable<Channel[]> {
    return this.validated(
      z.array(ChannelSchema),
      this.http.get<unknown>('/api/channels').pipe(catchError((e) => this.handleError(e))),
    );
  }

  getChannel(channelId: string): Observable<Channel> {
    return this.validated(
      ChannelSchema,
      this.http.get<unknown>(`/api/channels/${channelId}`).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getTopChannels(): Observable<ChannelStats[]> {
    return this.validated(
      z.array(ChannelStatsSchema),
      this.http.get<unknown>('/api/channels/top-performers').pipe(catchError((e) => this.handleError(e))),
    );
  }

  getChannelStats(channelId: string): Observable<ChannelStats> {
    return this.validated(
      ChannelStatsSchema,
      this.http.get<unknown>(`/api/channels/${channelId}/stats`).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getVideosForChannel(channelId: string): Observable<VideoSummary[]> {
    return this.validated(
      z.array(VideoSummarySchema),
      this.http.get<unknown>(`/api/channels/${channelId}/videos`).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getPicksForChannel(channelId: string): Observable<Pick[]> {
    return this.validated(
      z.array(PickSchema),
      this.http.get<unknown>(`/api/picks/channel/${channelId}`).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getRecentPicks(days = 30): Observable<Pick[]> {
    const params = new HttpParams().set('days', days.toString());
    return this.validated(
      z.array(PickSchema),
      this.http.get<unknown>('/api/picks', { params }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  triggerIngestion(): Observable<unknown> {
    return this.http.post('/api/admin/ingestion/run', null).pipe(catchError((e) => this.handleError(e)));
  }

  reextractVideo(videoId: string): Observable<unknown> {
    return this.http.post(`/api/admin/videos/${videoId}/reextract`, null).pipe(catchError((e) => this.handleError(e)));
  }

  getStockPrices(ticker: string, from: string, to: string): Observable<PricePoint[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.validated(
      z.array(PricePointSchema),
      this.http.get<unknown>(`/api/stocks/${ticker}/prices`, { params }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getPortfolios(): Observable<Portfolio[]> {
    return this.validated(
      z.array(PortfolioSchema),
      this.http.get<unknown>('/api/portfolios').pipe(catchError((e) => this.handleError(e))),
    );
  }

  getPortfolioPrices(channelId: string, from?: string): Observable<PortfolioPricePoint[]> {
    const params = from ? new HttpParams().set('from', from) : new HttpParams();
    return this.validated(
      z.array(PortfolioPricePointSchema),
      this.http.get<unknown>(`/api/portfolios/${channelId}/prices`, { params }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  checkHealth(): Observable<unknown> {
    return this.http.get('/api/admin/health');
  }
}
