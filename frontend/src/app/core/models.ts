export interface User {
  id: number;
  name: string;
  email: string;
  role: "USER" | "ADMIN";
  active: boolean;
  color: string;
  photo: string;
}
export interface House {
  id: number;
  name: string;
  emoji: string;
  photo: string;
  inviteCode: string;
  archived: boolean;
}
export interface Member extends User {
  membershipId: number;
  membershipRole: "OWNER" | "MEMBER";
  memberActive: boolean;
}
export interface Membership {
  id: number;
  houseId: number;
  userId: number;
  role: "OWNER" | "MEMBER";
  active: boolean;
}
export interface Expense {
  id: number;
  houseId: number;
  title: string;
  cents: number;
  paidBy: number;
  createdBy: number;
  date: string;
  category: string;
  shared: boolean;
  shares: { userId: number; cents: number }[];
}
export interface Repayment {
  id: number;
  houseId: number;
  fromUser: number;
  toUser: number;
  cents: number;
  date: string;
  createdBy: number;
}
export interface ShoppingItem {
  id: number;
  houseId: number;
  name: string;
  quantity: number;
  unit: string;
  bought: boolean;
}
export interface Purchase {
  id: number;
  houseId: number;
  name: string;
  quantity: number;
  unit: string;
  boughtBy: number;
  date: string;
}
export interface Frequent {
  name: string;
  quantity: number;
  unit: string;
  count: number;
}
export interface Task {
  id: number;
  houseId: number;
  title: string;
  notes: string;
  emoji: string;
  recurrence: "ONCE" | "WEEKLY";
  mode: "MANUAL" | "AUTO";
  active: boolean;
}
export interface Turn {
  id: number;
  taskId: number;
  userId: number;
  due: string;
  done: boolean;
  completedAt: string;
  titleSnapshot?: string;
  emojiSnapshot?: string;
}
export interface Notice {
  id: number;
  houseId: number;
  title: string;
  text: string;
  date: string;
  time: string;
  authorId: number;
}
export interface Settlement {
  fromUser: number;
  toUser: number;
  cents: number;
}
export interface Snapshot {
  user: User;
  house: House | null;
  members: Member[];
  expenses: Expense[];
  repayments: Repayment[];
  items: ShoppingItem[];
  frequent: Frequent[];
  tasks: Task[];
  turns: Turn[];
  notices: Notice[];
  myBalance: number;
  settlements: Settlement[];
  adminUsers: User[];
  adminHouses: House[];
}
export interface Database {
  version: number;
  nextId: number;
  users: User[];
  houses: House[];
  memberships: Membership[];
  expenses: Expense[];
  repayments: Repayment[];
  items: ShoppingItem[];
  purchases: Purchase[];
  tasks: Task[];
  turns: Turn[];
  notices: Notice[];
}
export type Payload = Record<string, any>;
export const UNITS = [
  "pz",
  "litri",
  "ml",
  "kg",
  "g",
  "pacchi",
  "flaconi",
  "confezioni",
];
export const CATEGORIES = ["Spesa", "Bollette", "Casa", "Altro"];
