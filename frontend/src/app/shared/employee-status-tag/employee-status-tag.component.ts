import { Component, computed, input } from '@angular/core';
import { EmployeeStatus } from '../../core/models';
import { LabelPipe } from '../label.pipe';

const TONE: Record<EmployeeStatus, string> = {
  ACTIVE: 'tag-success',
  ON_LEAVE: 'tag-warn',
  INACTIVE: '',
};

@Component({
  selector: 'app-employee-status-tag',
  imports: [LabelPipe],
  template: `<span class="tag" [class]="'tag ' + tone()">{{ status() | label }}</span>`,
})
export class EmployeeStatusTagComponent {
  readonly status = input.required<EmployeeStatus>();

  protected readonly tone = computed(() => TONE[this.status()]);
}
