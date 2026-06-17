import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { toSignal } from '@angular/core/rxjs-interop';
import { Observable, catchError, of, shareReplay, switchMap, timer } from 'rxjs';
import { StatusResponse } from '../models/status';
import { HistoryResponse } from '../models/history';

const STORAGE_KEY = 'cloudstub_host';
const DEFAULT_HOST = 'http://localhost:4567';
const POLL_INTERVAL_MS = 5000;

@Injectable({ providedIn: 'root' })
export class CloudStubApi {
  private readonly http = inject(HttpClient);

  /** Base URL of the CloudStub API port. Persisted to localStorage. */
  readonly host = signal(localStorage.getItem(STORAGE_KEY) ?? DEFAULT_HOST);

  /** Auto-refreshing status, polled every 5s; null while unreachable. */
  private readonly status$: Observable<StatusResponse | null> = timer(0, POLL_INTERVAL_MS).pipe(
    switchMap(() =>
      this.http.get<StatusResponse>(`${this.host()}/api/status`).pipe(catchError(() => of(null))),
    ),
    shareReplay(1),
  );

  /** Latest status snapshot as a signal — drives the whole UI. */
  readonly status = toSignal(this.status$, { initialValue: null });

  /** True while the most recent poll reached the server. */
  readonly connected = computed(() => this.status() !== null);

  setHost(host: string): void {
    const normalized = host.replace(/\/$/, '');
    localStorage.setItem(STORAGE_KEY, normalized);
    this.host.set(normalized);
  }

  getStatus(): Observable<StatusResponse> {
    return this.http.get<StatusResponse>(`${this.host()}/api/status`);
  }

  getHistory(serviceId?: string): Observable<HistoryResponse> {
    return this.http.get<HistoryResponse>(
      `${this.host()}/api/history${this.serviceQuery(serviceId)}`,
    );
  }

  reset(serviceId?: string): Observable<unknown> {
    return this.http.post(`${this.host()}/api/reset${this.serviceQuery(serviceId)}`, null);
  }

  callRoute(
    method: string,
    path: string,
    params: Record<string, string> = {},
  ): Observable<unknown> {
    const query = Object.entries(params)
      .filter(([, v]) => v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&');
    const url = `${this.host()}${path}${query ? '?' + query : ''}`;
    switch (method.toUpperCase()) {
      case 'GET':
        return this.http.get(url);
      case 'POST':
        return this.http.post(url, null);
      case 'PUT':
        return this.http.put(url, null);
      case 'DELETE':
        return this.http.delete(url);
      case 'PATCH':
        return this.http.patch(url, null);
      default:
        return this.http.request(method, url);
    }
  }

  private serviceQuery(serviceId?: string): string {
    return serviceId ? `?service=${encodeURIComponent(serviceId)}` : '';
  }
}
