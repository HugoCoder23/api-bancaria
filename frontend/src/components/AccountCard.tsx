import { Link } from 'react-router-dom';
import type { Account } from '../api/types';
import { ACCOUNT_STATUS_LABEL, ACCOUNT_TYPE_LABEL, fmtAccount, fmtMoney } from '../lib/format';

export function AccountCard({ account, showOwner }: { account: Account; showOwner?: boolean }) {
  return (
    <Link to={`/cuentas/${account.id}`} className={`account-card type-${account.type} is-${account.status}`}>
      <div className="ac-top">
        <span>{ACCOUNT_TYPE_LABEL[account.type]} · {account.currency}</span>
        {account.status !== 'ACTIVE' && <span className="ac-chip">{ACCOUNT_STATUS_LABEL[account.status]}</span>}
      </div>
      {showOwner && <div className="small">{account.customerName}</div>}
      <div className="ac-balance">{fmtMoney(account.balance, account.currency)}</div>
      <div className="ac-number">{fmtAccount(account.number)}</div>
    </Link>
  );
}
