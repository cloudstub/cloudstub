import {
  Component,
  WritableSignal,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { UpperCasePipe } from '@angular/common';
import { JsonHighlightPipe } from '../../core/pipes/json-highlight-pipe';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TagModule } from 'primeng/tag';
import { MessageService } from 'primeng/api';
import { CloudStubApi } from '../../core/services/cloudstub-api';
import { ApiRoute } from '../../core/models/status';

/** A route plus the transient, user-driven state of one invocation form. */
interface ExecutedRoute {
  route: ApiRoute;
  paramValues: Record<string, string>;
  result: WritableSignal<unknown>;
  error: WritableSignal<string | null>;
  loading: WritableSignal<boolean>;
}

@Component({
  selector: 'app-service-browser',
  imports: [
    UpperCasePipe,
    JsonHighlightPipe,
    FormsModule,
    ButtonModule,
    InputTextModule,
    TagModule,
  ],
  templateUrl: './service-browser.html',
  styleUrl: './service-browser.scss',
})
export class ServiceBrowser {
  private readonly route = inject(ActivatedRoute);
  readonly api = inject(CloudStubApi);
  private readonly toast = inject(MessageService);

  readonly serviceId = signal('');
  readonly executions = signal<ExecutedRoute[]>([]);

  readonly moduleInfo = computed(() =>
    this.api.status()?.modules.find((m) => m.id === this.serviceId()),
  );

  private readonly serviceRoutes = computed(
    () => this.api.status()?.routes.filter((r) => r.service === this.serviceId()) ?? [],
  );

  /** Stable identity of the current route set; only changes when routes do. */
  private readonly routeKey = computed(() =>
    this.serviceRoutes()
      .map((r) => `${r.method} ${r.path}`)
      .join('|'),
  );

  readonly hasRoutes = computed(() => this.serviceRoutes().length > 0);

  constructor() {
    this.route.paramMap
      .pipe(takeUntilDestroyed())
      .subscribe((params) => this.serviceId.set(params.get('id') ?? ''));

    // Rebuild the forms only when the route set genuinely changes
    effect(() => {
      this.routeKey();
      untracked(() => this.rebuildExecutions());
    });
  }

  private rebuildExecutions(): void {
    this.executions.set(
      this.serviceRoutes().map((r) => ({
        route: r,
        paramValues: Object.fromEntries((r.params ?? []).map((p) => [p.name, ''])),
        result: signal<unknown>(null),
        error: signal<string | null>(null),
        loading: signal(false),
      })),
    );
  }

  execute(exec: ExecutedRoute): void {
    exec.loading.set(true);
    exec.result.set(null);
    exec.error.set(null);
    this.api.callRoute(exec.route.method, exec.route.path, exec.paramValues).subscribe({
      next: (res) => {
        exec.result.set(res);
        exec.loading.set(false);
      },
      error: (err) => {
        const message = err?.error?.error ?? err.message ?? 'Unknown error';
        exec.error.set(message);
        exec.loading.set(false);
        this.toast.add({ severity: 'error', summary: 'Error', detail: message });
      },
    });
  }

  methodSeverity(method: string): 'success' | 'info' | 'warn' | 'danger' | 'secondary' {
    const map: Record<string, 'success' | 'info' | 'warn' | 'danger' | 'secondary'> = {
      GET: 'info',
      POST: 'success',
      PUT: 'warn',
      DELETE: 'danger',
    };
    return map[method] ?? 'secondary';
  }
}
