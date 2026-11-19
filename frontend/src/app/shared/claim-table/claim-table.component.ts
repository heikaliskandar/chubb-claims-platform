import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input, output } from '@angular/core';
import { Router } from '@angular/router';
import { AvatarModule } from 'primeng/avatar';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { ClaimSummary, PageResponse } from '../../core/models';
import { LabelPipe } from '../label.pipe';
import { marketName } from '../markets';
import { StatusTagComponent } from '../status-tag/status-tag.component';

@Component({
  selector: 'app-claim-table',
  imports: [TableModule, AvatarModule, CurrencyPipe, DatePipe, LabelPipe, StatusTagComponent],
  templateUrl: './claim-table.component.html',
  styleUrl: './claim-table.component.scss',
})
export class ClaimTableComponent {
  private readonly router = inject(Router);

  readonly page = input.required<PageResponse<ClaimSummary> | null>();
  readonly rows = input(10);
  readonly pageChange = output<number>();

  protected readonly marketName = marketName;

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.pageChange.emit(Math.floor((event.first ?? 0) / this.rows()));
  }

  protected open(claim: ClaimSummary): void {
    this.router.navigate(['/claims', claim.id]);
  }
}
