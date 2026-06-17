import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { MessageService } from 'primeng/api';
import { CloudStubApi } from '../../core/services/cloudstub-api';

const DEFAULT_HOST = 'http://localhost:4567';

@Component({
  selector: 'app-connect',
  imports: [FormsModule, InputTextModule, ButtonModule],
  templateUrl: './connect.html',
  styleUrl: './connect.scss',
})
export class Connect {
  readonly api = inject(CloudStubApi);
  private readonly toast = inject(MessageService);

  readonly hostInput = signal(this.api.host());
  readonly testing = signal(false);

  save(): void {
    const value = this.hostInput().trim();
    if (!value) return;
    this.api.setHost(value);
    this.toast.add({ severity: 'success', summary: 'Saved', detail: 'Connection updated' });
  }

  test(): void {
    this.testing.set(true);
    this.api.getStatus().subscribe({
      next: (s) => {
        this.testing.set(false);
        this.toast.add({
          severity: 'success',
          summary: 'Connected',
          detail: `Reached CloudStub on mock port ${s.port}`,
        });
      },
      error: () => {
        this.testing.set(false);
        this.toast.add({
          severity: 'error',
          summary: 'Failed',
          detail: 'Could not reach CloudStub at that address',
        });
      },
    });
  }

  resetToDefault(): void {
    this.hostInput.set(DEFAULT_HOST);
  }
}
