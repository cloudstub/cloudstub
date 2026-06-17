import { Component, computed, inject, signal } from '@angular/core';
import { NgClass, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { CloudStubApi } from '../../core/services/cloudstub-api';
import { RequestRecord } from '../../core/models/history';

@Component({
  selector: 'app-history',
  imports: [NgClass, DatePipe, FormsModule, TableModule, SelectModule, TagModule, ButtonModule],
  templateUrl: './history.html',
  styleUrl: './history.scss',
})
export class RequestHistory {
  readonly api = inject(CloudStubApi);

  readonly records = signal<RequestRecord[]>([]);
  readonly loading = signal(false);
  readonly selectedService = signal('');

  readonly serviceOptions = computed(() => {
    const mods = this.api.status()?.modules ?? [];
    return [
      { label: 'All services', value: '' },
      ...mods.map((m) => ({ label: m.id.toUpperCase(), value: m.id })),
    ];
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.api.getHistory(this.selectedService() || undefined).subscribe({
      next: (r) => {
        this.records.set(r.requests);
        this.loading.set(false);
      },
      error: () => {
        this.records.set([]);
        this.loading.set(false);
      },
    });
  }

  onServiceChange(service: string): void {
    this.selectedService.set(service);
    this.load();
  }

  statusSeverity(code: number): 'success' | 'info' | 'warn' | 'danger' {
    if (code < 300) return 'success';
    if (code < 400) return 'info';
    if (code < 500) return 'warn';
    return 'danger';
  }

  methodClass(method: string): string {
    return `method-${method.toLowerCase()}`;
  }
}
