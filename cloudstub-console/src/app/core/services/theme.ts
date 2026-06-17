import { Injectable, effect, signal } from '@angular/core';

const STORAGE_KEY = 'cloudstub_theme';

@Injectable({ providedIn: 'root' })
export class Theme {
  readonly dark = signal<boolean>(
    localStorage.getItem(STORAGE_KEY) === 'dark' ||
      (!localStorage.getItem(STORAGE_KEY) &&
        window.matchMedia('(prefers-color-scheme: dark)').matches),
  );

  constructor() {
    // Keep the document class and persisted preference in sync with the signal.
    effect(() => {
      const dark = this.dark();
      document.documentElement.classList.toggle('dark-mode', dark);
      localStorage.setItem(STORAGE_KEY, dark ? 'dark' : 'light');
    });
  }

  toggle(): void {
    this.dark.update((d) => !d);
  }
}
