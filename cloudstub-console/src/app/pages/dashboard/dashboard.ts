import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TagModule } from 'primeng/tag';
import { IconField } from 'primeng/iconfield';
import { InputIcon } from 'primeng/inputicon';
import { InputText } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';
import { CloudStubApi } from '../../core/services/cloudstub-api';
import { formatDuration } from '../../core/util/format';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, ButtonModule, TagModule, IconField, InputIcon, InputText],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  readonly api = inject(CloudStubApi);
  private readonly toast = inject(MessageService);

  readonly resetting = signal(false);
  readonly filterQuery = signal('');

  readonly status = this.api.status;
  readonly uptime = computed(() => {
    const uptime = this.status()?.uptime;
    return uptime ? formatDuration(uptime) : '—';
  });
  readonly totalStubs = computed(
    () => this.status()?.modules.reduce((sum, m) => sum + m.stubs.length, 0) ?? 0,
  );
  readonly filteredModules = computed(() => {
    const q = this.filterQuery().trim().toLowerCase();
    const modules = this.status()?.modules ?? [];
    return q ? modules.filter((m) => m.id.toLowerCase().includes(q)) : modules;
  });
  readonly startedAt = computed(() => {
    const startedAt = this.status()?.startedAt;
    return startedAt ? new Date(startedAt) : null;
  });

  resetAll(): void {
    this.resetting.set(true);
    this.api.reset().subscribe({
      next: () => {
        this.toast.add({ severity: 'success', summary: 'Reset', detail: 'All state cleared' });
        this.resetting.set(false);
      },
      error: () => {
        this.toast.add({ severity: 'error', summary: 'Error', detail: 'Reset failed' });
        this.resetting.set(false);
      },
    });
  }

  resetService(id: string): void {
    this.api.reset(id).subscribe({
      next: () =>
        this.toast.add({ severity: 'success', summary: 'Reset', detail: `${id} cleared` }),
      error: () => this.toast.add({ severity: 'error', summary: 'Error', detail: 'Reset failed' }),
    });
  }
}
