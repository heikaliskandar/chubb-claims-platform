import { Market } from '../core/models';

export interface MarketOption {
  code: Market;
  name: string;
  currency: string;
}

export const MARKETS: MarketOption[] = [
  { code: 'SG', name: 'Singapore', currency: 'SGD' },
  { code: 'MY', name: 'Malaysia', currency: 'MYR' },
  { code: 'HK', name: 'Hong Kong', currency: 'HKD' },
  { code: 'TH', name: 'Thailand', currency: 'THB' },
  { code: 'ID', name: 'Indonesia', currency: 'IDR' },
  { code: 'PH', name: 'Philippines', currency: 'PHP' },
];

export function marketName(code: Market): string {
  return MARKETS.find((market) => market.code === code)?.name ?? code;
}
