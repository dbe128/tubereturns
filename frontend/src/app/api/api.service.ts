import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { z } from 'zod';
import {
  ChannelSchema,
  VideoSummarySchema,
  PickSchema,
  PricePointSchema,
  PortfolioPricePointSchema,
  PipelineStepStatusSchema,
  ChannelSearchResultSchema,
  ChannelSuggestionSchema,
  MyChannelSuggestionSchema,
  RegisterResponseSchema,
  AuthResponseSchema,
  MessageResponseSchema,
  NotificationsStatusSchema,
  TickerDataSchema,
  UnknownStockSchema,
} from './types';
import type { Channel, VideoSummary, Pick, PricePoint, PortfolioPricePoint, PipelineStepStatus, ChannelSearchResult, ChannelSuggestion, MyChannelSuggestion, RegisterResponse, AuthResponse, MessageResponse, NotificationsStatus, TickerData, UnknownStock } from './types';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  private handleError(err: HttpErrorResponse): Observable<never> {
    if (err.status === 0) {
      return throwError(() => new Error('Cannot reach the backend.'));
    }
    if (err.status === 401) {
      const msg = (err.error as Record<string, string> | null)?.['message'];
      return throwError(() => new Error(msg ?? 'Invalid email and password combination'));
    }
    if (err.status === 403) {
      const msg = (err.error as Record<string, string> | null)?.['message'];
      return throwError(() => new Error(msg ?? 'Access denied'));
    }
    if (err.status === 400) {
      const msg = (err.error as Record<string, string> | null)?.['message'];
      return throwError(() => new Error(msg ?? 'Please check your input and try again'));
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

  getChannel(handle: string): Observable<Channel> {
    return this.validated(
      ChannelSchema,
      this.http.get<unknown>(`/api/channels/${handle}`).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getVideosForChannel(handle: string): Observable<VideoSummary[]> {
    return this.validated(
      z.array(VideoSummarySchema),
      this.http.get<unknown>(`/api/channels/${handle}/videos`).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getPicksForChannel(handle: string): Observable<Pick[]> {
    return this.validated(
      z.array(PickSchema),
      this.http.get<unknown>(`/api/picks/channel/${handle}`).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getRecentPicks(days = 30): Observable<Pick[]> {
    const params = new HttpParams().set('days', days.toString());
    return this.validated(
      z.array(PickSchema),
      this.http.get<unknown>('/api/picks', { params }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getPipelineStatus(): Observable<PipelineStepStatus[]> {
    return this.validated(
      z.array(PipelineStepStatusSchema),
      this.http.get<unknown>('/api/admin/pipeline/status').pipe(catchError((e) => this.handleError(e))),
    );
  }

  triggerPipelineStep(step: string): Observable<unknown> {
    return this.http.post<unknown>(`/api/admin/pipeline/${step}/trigger`, null).pipe(catchError((e) => this.handleError(e)));
  }

  getTickers(): Observable<TickerData> {
    return this.validated(
      TickerDataSchema,
      this.http.get<unknown>('/api/tickers').pipe(catchError((e) => this.handleError(e))),
    );
  }

  getMyChannelNotifications(): Observable<string[]> {
    return this.validated(
      z.array(z.string()),
      this.http.get<unknown>('/api/channels/my-notifications').pipe(catchError((e) => this.handleError(e))),
    );
  }

  subscribeToChannelNotification(handle: string): Observable<unknown> {
    return this.http.post<unknown>(`/api/channels/${handle}/notify`, null).pipe(catchError((e) => this.handleError(e)));
  }

  unsubscribeFromChannelNotification(handle: string): Observable<unknown> {
    return this.http.delete<unknown>(`/api/channels/${handle}/notify`).pipe(catchError((e) => this.handleError(e)));
  }

  getPendingNotifications(): Observable<NotificationsStatus> {
    return this.validated(
      NotificationsStatusSchema,
      this.http.get<unknown>('/api/admin/notifications/pending').pipe(catchError((e) => this.handleError(e))),
    );
  }

  triggerNotificationCheck(): Observable<unknown> {
    return this.http.post<unknown>('/api/admin/notifications/trigger', null).pipe(catchError((e) => this.handleError(e)));
  }

  getUnknownStocks(): Observable<UnknownStock[]> {
    return this.validated(
      z.array(UnknownStockSchema),
      this.http.get<unknown>('/api/admin/stocks/unknown').pipe(catchError((e) => this.handleError(e))),
    );
  }

  tryTicker(id: number, ticker: string, currency: string): Observable<MessageResponse> {
    return this.validated(
      MessageResponseSchema,
      this.http.post<unknown>(`/api/admin/stocks/${id}/try-ticker`, { ticker, currency }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  acceptUnknown(id: number): Observable<unknown> {
    return this.http.post<unknown>(`/api/admin/stocks/${id}/accept-unknown`, null).pipe(catchError((e) => this.handleError(e)));
  }

  reextractVideo(videoId: string): Observable<unknown> {
    return this.http.post<unknown>(`/api/admin/videos/${videoId}/reextract`, null).pipe(catchError((e) => this.handleError(e)));
  }

  redownloadTranscript(videoId: string): Observable<unknown> {
    return this.http.post<unknown>(`/api/admin/videos/${videoId}/redownload-transcript`, null).pipe(catchError((e) => this.handleError(e)));
  }

  setVideoExcluded(videoId: string, excluded: boolean): Observable<unknown> {
    const params = new HttpParams().set('excluded', excluded.toString());
    return this.http.patch<unknown>(`/api/admin/videos/${videoId}/excluded`, null, { params }).pipe(catchError((e) => this.handleError(e)));
  }

  deleteChannel(handle: string): Observable<unknown> {
    return this.http.delete<unknown>(`/api/admin/channels/${handle}`).pipe(catchError((e) => this.handleError(e)));
  }

  reprocessChannel(handle: string): Observable<unknown> {
    return this.http.post<unknown>(`/api/admin/channels/${handle}/reprocess`, null).pipe(catchError((e) => this.handleError(e)));
  }

  addChannel(handle: string, channelName: string, channelUrl: string, thumbnailUrl: string, description: string, subscriberCount: number | null, notifyOnComplete: boolean): Observable<unknown> {
    let params = new HttpParams()
      .set('channelName', channelName)
      .set('channelUrl', channelUrl)
      .set('thumbnailUrl', thumbnailUrl)
      .set('description', description)
      .set('notifyOnComplete', notifyOnComplete);
    if (subscriberCount != null) {
      params = params.set('subscriberCount', subscriberCount);
    }
    return this.http.post<unknown>(`/api/channels/${handle}/add`, null, { params }).pipe(catchError((e) => this.handleError(e)));
  }

  suggestChannel(handle: string, channelName: string, channelUrl: string, thumbnailUrl: string, description: string, subscriberCount: number | null, notifyOnComplete: boolean): Observable<MessageResponse> {
    let params = new HttpParams()
      .set('handle', handle)
      .set('channelName', channelName)
      .set('channelUrl', channelUrl)
      .set('thumbnailUrl', thumbnailUrl)
      .set('description', description)
      .set('notifyOnComplete', notifyOnComplete);
    if (subscriberCount != null) {
      params = params.set('subscriberCount', subscriberCount);
    }
    return this.validated(MessageResponseSchema, this.http.post<unknown>('/api/channel-suggestions', null, { params }).pipe(catchError((e) => this.handleError(e))));
  }

  getPendingChannelSuggestions(): Observable<ChannelSuggestion[]> {
    return this.validated(
      z.array(ChannelSuggestionSchema),
      this.http.get<unknown>('/api/admin/channel-suggestions').pipe(catchError((e) => this.handleError(e))),
    );
  }

  getMyChannelSuggestions(): Observable<MyChannelSuggestion[]> {
    return this.validated(
      z.array(MyChannelSuggestionSchema),
      this.http.get<unknown>('/api/channel-suggestions/my').pipe(catchError((e) => this.handleError(e))),
    );
  }

  setChannelSuggestionNotify(handle: string, enabled: boolean): Observable<unknown> {
    return this.http.patch<unknown>(`/api/channel-suggestions/${handle}/notify`, null, {
      params: new HttpParams().set('enabled', enabled),
    }).pipe(catchError((e) => this.handleError(e)));
  }

  deleteMyChannelSuggestion(handle: string): Observable<unknown> {
    return this.http.delete<unknown>(`/api/channel-suggestions/${handle}`).pipe(catchError((e) => this.handleError(e)));
  }

  addChannelSuggestion(handle: string): Observable<unknown> {
    return this.http.post<unknown>(`/api/admin/channel-suggestions/${handle}/add`, null).pipe(catchError((e) => this.handleError(e)));
  }

  rejectChannelSuggestion(handle: string): Observable<unknown> {
    return this.http.post<unknown>(`/api/admin/channel-suggestions/${handle}/reject`, null).pipe(catchError((e) => this.handleError(e)));
  }

  searchChannels(q: string, filterByKeywords: boolean): Observable<ChannelSearchResult[]> {
    return this.validated(
      z.array(ChannelSearchResultSchema),
      this.http.get<unknown>('/api/channels/search', { params: new HttpParams().set('q', q).set('filterByKeywords', filterByKeywords) }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getStockPrices(ticker: string, from: string, to: string): Observable<PricePoint[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.validated(
      z.array(PricePointSchema),
      this.http.get<unknown>(`/api/stocks/${ticker}/prices`, { params }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getPortfolioPrices(channelId: string, from?: string): Observable<PortfolioPricePoint[]> {
    const params = from ? new HttpParams().set('from', from) : new HttpParams();
    return this.validated(
      z.array(PortfolioPricePointSchema),
      this.http.get<unknown>(`/api/portfolios/${channelId}/prices`, { params }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getVersion(): Observable<string> {
    return this.http.get<{ version: string }>('/api/version').pipe(
      map((r) => r.version),
      catchError(() => of('?')),
    );
  }

  checkHealth(): Observable<unknown> {
    return this.http.get('/api/admin/health');
  }

  register(firstName: string, email: string, password: string): Observable<RegisterResponse> {
    return this.validated(
      RegisterResponseSchema,
      this.http.post<unknown>('/api/auth/register', { firstName, email, password }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  verifyEmail(token: string): Observable<unknown> {
    return this.http.get<unknown>('/api/auth/verify', { params: new HttpParams().set('token', token) })
      .pipe(catchError((e) => this.handleError(e)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.validated(
      AuthResponseSchema,
      this.http.post<unknown>('/api/auth/login', { email, password }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  getMe(): Observable<AuthResponse> {
    return this.validated(
      AuthResponseSchema,
      this.http.get<unknown>('/api/auth/me').pipe(catchError((e) => this.handleError(e))),
    );
  }

  forgotPassword(email: string): Observable<MessageResponse> {
    return this.validated(
      MessageResponseSchema,
      this.http.post<unknown>('/api/auth/forgot-password', { email }).pipe(catchError((e) => this.handleError(e))),
    );
  }

  resetPassword(token: string, newPassword: string): Observable<MessageResponse> {
    return this.validated(
      MessageResponseSchema,
      this.http.post<unknown>('/api/auth/reset-password', { token, newPassword }).pipe(catchError((e) => this.handleError(e))),
    );
  }
}
