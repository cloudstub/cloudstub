export interface StubInfo {
  protocol: string;
  matchKey: string;
}

export interface ModuleStatus {
  id: string;
  stubs: StubInfo[];
}

export interface RouteParam {
  name: string;
  required: boolean;
  description: string;
}

export interface QueryParam {
  name: string;
  description: string;
}

export interface ApiRoute {
  method: string;
  path: string;
  description: string;
  service?: string;
  command?: string;
  params?: RouteParam[];
  queryParams?: QueryParam[];
}

export interface StatusResponse {
  port: number;
  apiPort: number;
  startedAt: string;
  uptime: string;
  modules: ModuleStatus[];
  routes: ApiRoute[];
}
