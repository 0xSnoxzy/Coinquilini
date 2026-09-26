import { Component, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
import { Modal } from "../shared/modal";
import {
  CATEGORIES,
  Expense,
  Payload,
  Repayment,
  Settlement,
} from "../core/models";
import { today } from "../core/dates";
// Responsabilità suggerita: David: spese, quote esatte in centesimi, saldi e rimborsi registrati.
@Component({
  selector: "cq-finance",
  standalone: true,
  imports: [FormsModule, Modal],
  template: `<div class="page-title">
      <div>
        <h1>Spese, senza pensieri.</h1>
        <p>Le spese che ti riguardano e i rimborsi registrati.</p>
      </div>
      <div class="actions">
        <button (click)="repay()">Registra rimborso</button
        ><button class="primary" (click)="edit()">＋ Aggiungi spesa</button>
      </div>
    </div>
    <div class="grid finance-grid">
      <section class="panel">
        <div class="section-title">
          <h2>Movimenti</h2>
          <input
            type="search"
            [(ngModel)]="query"
            placeholder="Cerca una spesa"
            aria-label="Cerca una spesa"
          />
        </div>
        @for (e of filtered(); track e.id) {
          <article class="expense-row">
            <div class="row no-border">
              <span class="expense-icon">{{
                e.category === "Bollette"
                  ? "⚡"
                  : e.category === "Spesa"
                    ? "🛒"
                    : "🧾"
              }}</span>
              <div class="grow">
                <strong>{{ e.title }}</strong
                ><small
                  >{{ s.name(e.paidBy) }} ha pagato · {{ s.date(e.date) }} ·
                  {{ e.category }}</small
                >
              </div>
              <strong>{{ s.euros(e.cents) }}</strong>
            </div>
            <div class="expense-details">
              <small
                >{{
                  e.shared
                    ? "Condivisa con tutta la casa"
                    : "Solo tra i partecipanti"
                }}
                · {{ shares(e) }}</small
              >
              @if (s.canEdit(e.createdBy)) {
                <div class="actions">
                  <button (click)="edit(e)">Modifica</button
                  ><button (click)="remove(e)">Rimuovi</button>
                </div>
              }
            </div>
          </article>
        } @empty {
          <p class="empty">Nessuna spesa da mostrare.</p>
        }
      </section>
      <div class="stack">
        <section class="panel">
          <h2>Il tuo saldo</h2>
          <div class="big-number">{{ s.euros(s.data()!.myBalance) }}</div>
          <p class="muted">
            {{
              s.data()!.myBalance > 0
                ? "Devi ricevere questo importo."
                : s.data()!.myBalance < 0
                  ? "Devi restituire questo importo."
                  : "I tuoi conti sono in pari."
            }}
          </p>
        </section>
        <section class="panel">
          <h2>Rimborsi suggeriti</h2>
          @for (r of s.data()!.settlements; track $index) {
            <div class="settlement">
              <strong>{{ s.name(r.fromUser) }} → {{ s.name(r.toUser) }}</strong
              ><span>{{ s.euros(r.cents) }}</span
              ><button (click)="repay(r)">Registra rimborso</button>
            </div>
          } @empty {
            <p class="empty">Nessun rimborso suggerito per te.</p>
          }
          <small>Registra il rimborso dopo aver scambiato il denaro.</small>
        </section>
      </div>
    </div>
    <section class="panel top-gap">
      <h2>Rimborsi registrati</h2>
      @for (r of s.data()!.repayments; track r.id) {
        <div class="row">
          <div class="grow">
            <strong>{{ s.name(r.fromUser) }} → {{ s.name(r.toUser) }}</strong
            ><small>{{ s.date(r.date) }}</small>
          </div>
          <strong>{{ s.euros(r.cents) }}</strong>
          @if (s.canEdit(r.createdBy)) {
            <button
              class="icon-button"
              (click)="removeRepayment(r)"
              aria-label="Rimuovi registrazione rimborso"
            >
              ×
            </button>
          }
        </div>
      } @empty {
        <p class="empty">Nessun rimborso registrato.</p>
      }
    </section>
    @if (draft) {
      <cq-modal
        [title]="draft['id'] ? 'Modifica spesa' : 'Aggiungi spesa'"
        (closed)="draft = null"
        ><form #f="ngForm" (ngSubmit)="save()">
          <label
            >Descrizione<input
              name="title"
              [(ngModel)]="draft['title']"
              required
              maxlength="100"
          /></label>
          <div class="form-grid">
            <label
              >Importo (€)<input
                type="number"
                name="amount"
                [(ngModel)]="draft['amount']"
                required
                min="0.01"
                max="1000000"
                step="0.01" /></label
            ><label
              >Data<input
                type="date"
                name="date"
                [(ngModel)]="draft['date']"
                required /></label
            ><label
              >Ha pagato<select name="paidBy" [(ngModel)]="draft['paidBy']">
                @for (m of s.activeMembers(); track m.id) {
                  <option [ngValue]="m.id">{{ m.name }}</option>
                }
              </select></label
            ><label
              >Categoria<select name="category" [(ngModel)]="draft['category']">
                @for (c of categories; track c) {
                  <option>{{ c }}</option>
                }
              </select></label
            >
          </div>
          <fieldset>
            <legend>Dividi in parti uguali tra</legend>
            <div class="check-grid">
              @for (m of s.activeMembers(); track m.id) {
                <label class="check"
                  ><input
                    type="checkbox"
                    [checked]="selected.includes(m.id)"
                    (change)="select(m.id, $event)"
                  />{{ m.name }}</label
                >
              }
            </div>
          </fieldset>
          <p class="muted">
            La bacheca comune mostrerà questa spesa solo se partecipano tutti.
          </p>
          <button
            class="primary"
            [disabled]="f.invalid || !selected.length || s.busy()"
          >
            Salva spesa
          </button>
        </form></cq-modal
      >
    }
    @if (refund) {
      <cq-modal title="Registra rimborso" (closed)="refund = null"
        ><form #f="ngForm" (ngSubmit)="saveRefund()">
          <div class="form-grid">
            <label
              >Da<select name="fromUser" [(ngModel)]="refund['fromUser']">
                @for (m of s.activeMembers(); track m.id) {
                  <option [ngValue]="m.id">{{ m.name }}</option>
                }
              </select></label
            ><label
              >A<select name="toUser" [(ngModel)]="refund['toUser']">
                @for (m of s.activeMembers(); track m.id) {
                  <option [ngValue]="m.id">{{ m.name }}</option>
                }
              </select></label
            ><label
              >Importo (€)<input
                type="number"
                name="amount"
                [(ngModel)]="refund['amount']"
                required
                min="0.01"
                max="1000000"
                step="0.01" /></label
            ><label
              >Data<input
                type="date"
                name="date"
                [(ngModel)]="refund['date']"
                required
            /></label>
          </div>
          <p class="muted">
            Conferma un rimborso già effettuato tra coinquilini.
          </p>
          <button
            class="primary"
            [disabled]="
              f.invalid || refund['fromUser'] === refund['toUser'] || s.busy()
            "
          >
            Registra rimborso
          </button>
        </form></cq-modal
      >
    }`,
})
export class Finance {
  s = inject(Store);
  draft: Payload | null = null;
  refund: Payload | null = null;
  selected: number[] = [];
  categories = CATEGORIES;
  query = "";
  filtered() {
    return [...this.s.data()!.expenses]
      .filter((e) => e.title.toLowerCase().includes(this.query.toLowerCase()))
      .sort((a, b) => b.date.localeCompare(a.date) || b.id - a.id);
  }
  shares(e: Expense) {
    return e.shares
      .map((q) => this.s.name(q.userId) + ": " + this.s.euros(q.cents))
      .join(" · ");
  }
  edit(e?: Expense) {
    this.selected = e
      ? e.shares.map((q) => q.userId)
      : this.s.activeMembers().map((m) => m.id);
    this.draft = e
      ? { ...e, amount: e.cents / 100 }
      : {
          title: "",
          amount: null,
          date: today(),
          paidBy: this.s.user()!.id,
          category: "Casa",
        };
  }
  select(id: number, event: Event) {
    this.selected = (event.target as HTMLInputElement).checked
      ? [...this.selected, id]
      : this.selected.filter((x) => x !== id);
  }
  async save() {
    if (
      await this.s.send("finance/save", {
        ...this.draft,
        cents: Math.round(this.draft!["amount"] * 100),
        participants: this.selected,
      })
    )
      this.draft = null;
  }
  remove(e: Expense) {
    if (confirm("Rimuovere questa spesa e ricalcolare i saldi?"))
      void this.s.send("finance/delete", { id: e.id });
  }
  repay(r?: Settlement) {
    this.refund = {
      fromUser: r?.fromUser || this.s.user()!.id,
      toUser:
        r?.toUser ||
        this.s.activeMembers().find((m) => m.id !== this.s.user()!.id)?.id,
      amount: r ? r.cents / 100 : null,
      date: today(),
    };
  }
  async saveRefund() {
    if (
      await this.s.send("finance/repay", {
        ...this.refund,
        cents: Math.round(this.refund!["amount"] * 100),
      })
    )
      this.refund = null;
  }
  removeRepayment(r: Repayment) {
    if (confirm("Rimuovere la registrazione di questo rimborso?"))
      void this.s.send("finance/delete-repayment", { id: r.id });
  }
}
