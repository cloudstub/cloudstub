import { Component, computed, inject } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { CloudStubApi } from '../../core/services/cloudstub-api';
import { Theme } from '../../core/services/theme';
import { formatDuration } from '../../core/util/format';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, UpperCasePipe, ButtonModule, ToastModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  readonly api = inject(CloudStubApi);
  readonly theme = inject(Theme);

  readonly modules = computed(() => this.api.status()?.modules ?? []);
  readonly uptime = computed(() => {
    const uptime = this.api.status()?.uptime;
    return uptime ? formatDuration(uptime) : '—';
  });

  toggleTheme(): void {
    this.theme.toggle();
  }
}
