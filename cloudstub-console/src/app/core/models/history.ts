export interface RequestRecord {
  timestamp: string;
  method: string;
  url: string;
  serviceId: string;
  operation: string;
  statusCode: number;
  matched: boolean;
}

export interface HistoryResponse {
  requests: RequestRecord[];
}
