export type Role = 'CUSTOMER' | 'OPERATOR' | 'ADMIN';
export type CustomerStatus = 'ACTIVE' | 'BLOCKED';
export type AccountType = 'SAVINGS' | 'CHECKING';
export type AccountStatus = 'ACTIVE' | 'BLOCKED' | 'CLOSED';
export type Currency = 'MXN' | 'USD' | 'EUR';
export type TransactionType = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';
export type TransactionStatus = 'COMPLETED' | 'REJECTED';
export type AuditResult = 'SUCCESS' | 'REJECTED' | 'DENIED';

export interface ApiErrorBody {
  status: number;
  code?: string;
  message: string;
  reference?: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  customerId?: number;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Customer {
  id: number;
  fullName: string;
  email: string;
  phone: string;
  status: CustomerStatus;
  accountCount: number;
  createdAt: string;
}

export interface Account {
  id: number;
  number: string;
  type: AccountType;
  currency: Currency;
  balance: number;
  status: AccountStatus;
  openedAt: string;
  customerId: number;
  customerName: string;
}

export interface Transaction {
  reference: string;
  type: TransactionType;
  status: TransactionStatus;
  amount: number;
  currency: Currency;
  sourceAccountId?: number;
  sourceAccountNumber?: string;
  sourceBalanceAfter?: number;
  destinationAccountId?: number;
  destinationAccountNumber?: string;
  destinationBalanceAfter?: number;
  description?: string;
  failureCode?: string;
  failureReason?: string;
  createdAt: string;
}

export interface HistoryLine {
  reference: string;
  type: TransactionType;
  status: TransactionStatus;
  direction: 'CREDIT' | 'DEBIT';
  amount: number;
  balanceAfter?: number;
  counterpartyAccountNumber?: string;
  description?: string;
  failureReason?: string;
  createdAt: string;
}

export interface CurrencyTotal {
  currency: Currency;
  amount: number;
  count: number;
}

export interface Summary {
  customers: number;
  activeAccounts: number;
  blockedAccounts: number;
  completedToday: number;
  rejectedToday: number;
  balances: CurrencyTotal[];
  volumeToday: CurrencyTotal[];
}

export interface AuditEvent {
  id: number;
  actor: string;
  action: string;
  resource?: string;
  result: AuditResult;
  detail?: string;
  createdAt: string;
}
