export type Role = 'CLAIMANT' | 'OFFICER' | 'MANAGER';

export type Market = 'SG' | 'MY' | 'HK' | 'TH' | 'ID' | 'PH';

export type ClaimType = 'MOTOR' | 'PROPERTY';

export type ClaimStatus =
  | 'SUBMITTED'
  | 'ASSIGNED'
  | 'UNDER_REVIEW'
  | 'INFO_REQUESTED'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'SETTLED'
  | 'WITHDRAWN';

export type Party = 'CLAIMS_TEAM' | 'ASSIGNED_OFFICER' | 'CLAIMANT' | 'APPROVER' | 'NOBODY';

export type ClaimAction =
  | 'ASSIGN_TO_ME'
  | 'START_REVIEW'
  | 'REQUEST_INFORMATION'
  | 'RESPOND_TO_INFORMATION_REQUEST'
  | 'ASSESS'
  | 'APPROVE'
  | 'REJECT'
  | 'APPROVE_REFERRAL'
  | 'RETURN_REFERRAL'
  | 'REJECT_REFERRAL'
  | 'SETTLE'
  | 'EDIT_DETAILS'
  | 'WITHDRAW';

export interface CurrentUser {
  username: string;
  role: Role;
  position: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ClaimSummary {
  id: string;
  claimNumber: string;
  market: Market;
  claimType: ClaimType;
  status: ClaimStatus;
  waitingOn: Party;
  currency: string;
  claimedAmount: number;
  assignedOfficerId: string | null;
  incidentDate: string;
  createdAt: string;
  updatedAt: string;
}

export interface ClaimDetail extends ClaimSummary {
  policyNumber: string;
  claimantId: string;
  incidentDescription: string;
  nextStep: string;
  allowedActions: ClaimAction[];
  reserveAmount: number | null;
  decision: {
    outcome: 'APPROVED' | 'REJECTED';
    approvedAmount: number | null;
    reason: string;
    decidedBy: string;
    decidedAt: string;
  } | null;
  referral: { referredBy: string; amount: number; reason: string; referredAt: string } | null;
  statusHistory: {
    fromStatus: ClaimStatus | null;
    toStatus: ClaimStatus;
    changedBy: string;
    reason: string | null;
    changedAt: string;
  }[];
  informationRequests: {
    id: string;
    requestedBy: string;
    question: string;
    response: string | null;
    status: 'OPEN' | 'ANSWERED';
    requestedAt: string;
    answeredAt: string | null;
  }[];
  assessments:
    | {
        officerId: string;
        assessedAmount: number;
        currency: string;
        notes: string | null;
        assessedAt: string;
      }[]
    | null;
  canUploadDocuments: boolean;
}

export interface SubmitClaimRequest {
  policyNumber: string;
  market: Market;
  claimType: ClaimType;
  incidentDate: string;
  incidentDescription: string;
  claimedAmount: number;
}

export interface ExposureReport {
  asOf: string | null;
  lines: {
    market: Market;
    claimType: ClaimType;
    currency: string;
    openClaims: number;
    outstandingReserve: number;
  }[];
  totalsByCurrency: { currency: string; openClaims: number; outstandingReserve: number }[];
}

export interface WorkloadReport {
  asOf: string | null;
  unassignedClaims: number;
  teams: {
    team: string;
    openClaims: number;
    officers: {
      officerId: string;
      openClaims: number;
      claimsByStatus: Partial<Record<ClaimStatus, number>>;
    }[];
  }[];
}

export interface PerformanceReport {
  asOf: string | null;
  teams: {
    team: string;
    decided: number;
    approved: number;
    rejected: number;
    closed: number;
    averageHoursToDecision: number | null;
  }[];
}

export interface ProblemDetail {
  title?: string;
  status?: number;
  detail?: string;
  errors?: Record<string, string>;
}

export type Position = 'CLAIMS_OFFICER' | 'SENIOR_CLAIMS_OFFICER' | 'TEAM_LEAD' | 'CLAIMS_MANAGER';

export type EmployeeStatus = 'ACTIVE' | 'ON_LEAVE' | 'INACTIVE';

export interface EmployeeSummary {
  username: string;
  employeeNumber: string;
  fullName: string;
  email: string;
  position: Position;
  team: string;
  market: Market;
  managerUsername: string | null;
  joinDate: string;
  status: EmployeeStatus;
}

export interface Person {
  username: string;
  fullName: string;
  position: Position;
}

export interface EmployeeDetail extends Omit<EmployeeSummary, 'managerUsername'> {
  phone: string | null;
  department: string;
  officeLocation: string | null;
  employmentType: 'FULL_TIME' | 'PART_TIME' | 'CONTRACT';
  manager: Person | null;
  directReports: Person[];
  totalReports: number;
  approvalAuthority: { unlimited: boolean; limits: { currency: string; amount: number }[] };
  self: boolean;
}

export type ApprovalView = 'TO_REVIEW' | 'DECIDED' | 'MY_REFERRALS';

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'RETURNED' | 'REJECTED';

export interface Approval {
  claimId: string;
  claimNumber: string;
  market: Market;
  claimType: ClaimType;
  currency: string;
  claimedAmount: number;
  recommendedAmount: number;
  reason: string;
  referredBy: string;
  referredAt: string;
  status: ApprovalStatus;
  reviewedBy: string | null;
  reviewedAt: string | null;
  canReview: boolean;
}

export type DocumentCategory =
  | 'PHOTO'
  | 'POLICE_REPORT'
  | 'REPAIR_QUOTE'
  | 'INVOICE'
  | 'PROOF_OF_OWNERSHIP'
  | 'ADJUSTER_REPORT'
  | 'OTHER';

export interface ClaimDocument {
  id: string;
  category: DocumentCategory;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  uploadedBy: string;
  uploadedAt: string;
  canRemove: boolean;
}
