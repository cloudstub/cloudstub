import { Routes } from '@angular/router';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  {
    path: '',
    component: Shell,
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'history',
        loadComponent: () => import('./pages/history/history').then((m) => m.RequestHistory),
      },
      {
        path: 'services/:id',
        loadComponent: () =>
          import('./pages/service-browser/service-browser').then((m) => m.ServiceBrowser),
      },
      {
        path: 'connect',
        loadComponent: () => import('./pages/connect/connect').then((m) => m.Connect),
      },
    ],
  },
];
