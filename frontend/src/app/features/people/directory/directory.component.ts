import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TreeNode } from 'primeng/api';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { OrganizationChartModule } from 'primeng/organizationchart';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { combineLatest, debounceTime, switchMap } from 'rxjs';
import { AuthService } from '../../../core/auth.service';
import { EmployeesApiService } from '../../../core/employees-api.service';
import { EmployeeSummary, PageResponse } from '../../../core/models';
import { PageHeaderComponent } from '../../../layout/page-header/page-header.component';
import { EmployeeStatusTagComponent } from '../../../shared/employee-status-tag/employee-status-tag.component';
import { LabelPipe } from '../../../shared/label.pipe';
import { marketName } from '../../../shared/markets';
import { UserAvatarComponent } from '../../../shared/user-avatar/user-avatar.component';

type View = 'list' | 'chart';

const PAGE_SIZE = 10;
const CHART_SIZE = 100;

@Component({
  selector: 'app-directory',
  imports: [
    DatePipe,
    FormsModule,
    IconFieldModule,
    InputIconModule,
    InputTextModule,
    OrganizationChartModule,
    SelectButtonModule,
    TableModule,
    LabelPipe,
    PageHeaderComponent,
    EmployeeStatusTagComponent,
    UserAvatarComponent,
  ],
  templateUrl: './directory.component.html',
  styleUrl: './directory.component.scss',
})
export class DirectoryComponent {
  private readonly api = inject(EmployeesApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly pageSize = PAGE_SIZE;
  protected readonly marketName = marketName;
  protected readonly views = [
    { value: 'list' as View, icon: 'pi pi-list', label: 'List' },
    { value: 'chart' as View, icon: 'pi pi-sitemap', label: 'Org chart' },
  ];
  protected readonly view = signal<View>('list');
  protected readonly query = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly result = signal<PageResponse<EmployeeSummary> | null>(null);

  protected readonly orgChart = computed<TreeNode[]>(() => {
    const people = this.result()?.content ?? [];
    const root = people.find((person) => person.username === this.auth.user()?.username);
    return root && this.view() === 'chart' ? [toNode(root, people)] : [];
  });

  constructor() {
    combineLatest([
      toObservable(this.query).pipe(debounceTime(250)),
      toObservable(this.pageIndex),
      toObservable(this.view),
    ])
      .pipe(
        switchMap(([query, page, view]) =>
          view === 'chart'
            ? this.api.directory('', 0, CHART_SIZE)
            : this.api.directory(query, page, PAGE_SIZE),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((result) => this.result.set(result));
  }

  protected search(query: string): void {
    this.query.set(query);
    this.pageIndex.set(0);
  }

  protected onLazyLoad(event: TableLazyLoadEvent): void {
    this.pageIndex.set(Math.floor((event.first ?? 0) / PAGE_SIZE));
  }

  protected open(username: string): void {
    this.router.navigate(['/people', username]);
  }
}

function toNode(person: EmployeeSummary, people: EmployeeSummary[]): TreeNode {
  return {
    expanded: true,
    data: person,
    children: people
      .filter((candidate) => candidate.managerUsername === person.username)
      .map((report) => toNode(report, people)),
  };
}
