import { Component, input, output } from '@angular/core';
import { EmployeeSummary } from '../../core/models';
import { LabelPipe } from '../label.pipe';
import { UserAvatarComponent } from '../user-avatar/user-avatar.component';

export interface OrgNode {
  person: EmployeeSummary;
  children: OrgNode[];
}

@Component({
  selector: 'app-org-tree',
  imports: [LabelPipe, UserAvatarComponent],
  template: `
    <ul [class.root]="root()">
      @for (node of nodes(); track node.person.username) {
        <li>
          <button type="button" class="node" (click)="selected.emit(node.person.username)">
            <app-user-avatar [username]="node.person.username" />
            <span class="who">
              <strong>{{ node.person.fullName }}</strong>
              <span>{{ node.person.position | label }} · {{ node.person.team }}</span>
            </span>
            @if (node.children.length) {
              <span class="count">{{ node.children.length }}</span>
            }
          </button>
          @if (node.children.length) {
            <app-org-tree
              [nodes]="node.children"
              [root]="false"
              (selected)="selected.emit($event)"
            />
          }
        </li>
      }
    </ul>
  `,
  styles: `
    ul {
      margin: 0;
      padding: 0 0 0 28px;
      list-style: none;
    }
    ul.root {
      padding-left: 0;
    }
    li {
      position: relative;
      padding-top: 10px;
    }
    ul:not(.root) > li::before {
      content: '';
      position: absolute;
      top: 0;
      bottom: 0;
      left: -16px;
      border-left: 2px solid var(--app-border);
    }
    ul:not(.root) > li:last-child::before {
      bottom: auto;
      height: 38px;
    }
    ul:not(.root) > li::after {
      content: '';
      position: absolute;
      top: 38px;
      left: -16px;
      width: 14px;
      border-top: 2px solid var(--app-border);
    }
    .node {
      display: flex;
      align-items: center;
      gap: 12px;
      width: min(100%, 420px);
      padding: 10px 14px;
      border: 1px solid var(--app-border);
      border-radius: var(--app-radius-md);
      background: var(--app-surface);
      text-align: left;
      cursor: pointer;
      transition:
        border-color 0.15s,
        box-shadow 0.15s;
    }
    .node:hover {
      border-color: var(--app-primary);
      box-shadow: var(--app-shadow);
    }
    .who {
      display: flex;
      flex: 1;
      flex-direction: column;
      min-width: 0;
    }
    .who strong {
      color: var(--app-heading);
    }
    .who span {
      color: var(--app-muted);
      font-size: 12px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .count {
      padding: 2px 8px;
      border-radius: var(--app-radius-full);
      background: var(--app-primary-soft);
      color: var(--app-primary);
      font-size: 12px;
      font-weight: 700;
    }
    @media (max-width: 480px) {
      ul {
        padding-left: 18px;
      }
      ul:not(.root) > li::before,
      ul:not(.root) > li::after {
        left: -11px;
      }
      ul:not(.root) > li::after {
        width: 9px;
      }
    }
  `,
})
export class OrgTreeComponent {
  readonly nodes = input.required<OrgNode[]>();
  readonly root = input(true);
  readonly selected = output<string>();
}
