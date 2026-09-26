import { Component, inject } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Store } from "../core/store";
import { Modal } from "../shared/modal";
import { Payload, ShoppingItem, Frequent, UNITS } from "../core/models";
import { today } from "../core/dates";
// Responsabilità suggerita: Elisa: quantità, prodotti frequenti e acquisto collegato alle spese.
@Component({
  selector: "cq-shopping",
  standalone: true,
  imports: [FormsModule, Modal],
  template: `<div class="page-title">
      <div>
        <h1>Cosa manca in casa?</h1>
        <p>Una lista condivisa, pronta quando siete al supermercato.</p>
      </div>
      <button class="primary" (click)="edit()">＋ Aggiungi prodotto</button>
    </div>
    <section class="panel">
      @for (i of s.data()!.items; track i.id) {
        <div class="row shopping-row">
          <label class="check grow" [class.strike]="i.bought"
            ><input
              type="checkbox"
              [checked]="i.bought"
              [disabled]="s.busy()"
              (change)="toggle(i, $event)"
            /><span>{{ i.name }}</span></label
          ><button (click)="edit(i)" [attr.aria-label]="'Modifica ' + i.name">
            {{ i.quantity }} {{ i.unit }} ✎</button
          ><button
            class="icon-button"
            (click)="remove(i)"
            [attr.aria-label]="'Rimuovi ' + i.name"
          >
            ×
          </button>
        </div>
      } @empty {
        <p class="empty">La lista è vuota. Aggiungi quello che serve.</p>
      }
      @if (bought().length) {
        <div class="section-footer">
          <button class="primary" (click)="checkout()">
            Registra acquisto · {{ bought().length }} prodotti</button
          ><small>Inserisci il totale pagato e scegli con chi dividerlo.</small>
        </div>
      }
    </section>
    @if (draft) {
      <cq-modal
        [title]="draft['id'] ? 'Modifica prodotto' : 'Aggiungi prodotto'"
        (closed)="draft = null"
        ><form #f="ngForm" (ngSubmit)="save()">
          <label
            >Prodotto<input
              name="name"
              [(ngModel)]="draft['name']"
              required
              maxlength="70"
          /></label>
          <div class="form-grid">
            <label
              >Quantità<input
                type="number"
                name="quantity"
                [(ngModel)]="draft['quantity']"
                required
                min="0.01"
                max="9999"
                step="0.01" /></label
            ><label
              >Unità<select name="unit" [(ngModel)]="draft['unit']">
                @for (u of units; track u) {
                  <option>{{ u }}</option>
                }
              </select></label
            >
          </div>
          @if (!draft["id"] && s.data()!.frequent.length) {
            <section class="frequent">
              <h3>Comprati spesso</h3>
              <p class="muted">Seleziona un prodotto e regola la quantità.</p>
              <div class="frequent-grid">
                @for (p of s.data()!.frequent; track p.name + p.unit) {
                  <button type="button" (click)="choose(p)">
                    <strong>{{ p.name }}</strong
                    ><small>{{ p.quantity }} {{ p.unit }}</small>
                  </button>
                }
              </div>
            </section>
          }
          <button class="primary" [disabled]="f.invalid || s.busy()">
            {{ draft["id"] ? "Salva modifiche" : "Aggiungi alla lista" }}
          </button>
        </form></cq-modal
      >
    }
    @if (purchase) {
      <cq-modal title="Registra acquisto" (closed)="purchase = null"
        ><form #f="ngForm" (ngSubmit)="savePurchase()">
          <label
            >Descrizione<input
              name="title"
              [(ngModel)]="purchase['title']"
              required
              maxlength="100"
          /></label>
          <div class="form-grid">
            <label
              >Totale (€)<input
                type="number"
                name="amount"
                [(ngModel)]="purchase['amount']"
                required
                min="0.01"
                max="1000000"
                step="0.01" /></label
            ><label
              >Data<input
                type="date"
                name="date"
                [(ngModel)]="purchase['date']"
                required
            /></label>
          </div>
          <label
            >Ha pagato<select name="paidBy" [(ngModel)]="purchase['paidBy']">
              @for (m of s.activeMembers(); track m.id) {
                <option [ngValue]="m.id">{{ m.name }}</option>
              }
            </select></label
          >
          <fieldset>
            <legend>Dividi tra</legend>
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
          <button
            class="primary"
            [disabled]="f.invalid || !selected.length || s.busy()"
          >
            Salva acquisto
          </button>
        </form></cq-modal
      >
    }`,
})
export class Shopping {
  s = inject(Store);
  draft: Payload | null = null;
  purchase: Payload | null = null;
  units = UNITS;
  selected: number[] = [];
  bought() {
    return this.s.data()!.items.filter((i) => i.bought);
  }
  edit(i?: ShoppingItem) {
    this.draft = i ? { ...i } : { name: "", quantity: 1, unit: "pz" };
  }
  choose(p: Frequent) {
    Object.assign(this.draft!, {
      name: p.name,
      quantity: p.quantity,
      unit: p.unit,
    });
  }
  async save() {
    if (await this.s.send("shopping/save", this.draft!)) this.draft = null;
  }
  toggle(i: ShoppingItem, event: Event) {
    void this.s.send(
      "shopping/toggle",
      { id: i.id, bought: (event.target as HTMLInputElement).checked },
      "Lista aggiornata.",
    );
  }
  remove(i: ShoppingItem) {
    if (confirm("Rimuovere " + i.name + " dalla lista?"))
      void this.s.send("shopping/delete", { id: i.id });
  }
  checkout() {
    this.selected = this.s.activeMembers().map((m) => m.id);
    this.purchase = {
      title: "Spesa condivisa",
      amount: null,
      date: today(),
      paidBy: this.s.user()!.id,
      category: "Spesa",
      items: this.bought().map((i) => i.id),
    };
  }
  select(id: number, event: Event) {
    this.selected = (event.target as HTMLInputElement).checked
      ? [...this.selected, id]
      : this.selected.filter((x) => x !== id);
  }
  async savePurchase() {
    if (
      await this.s.send(
        "shopping/checkout",
        {
          ...this.purchase,
          cents: Math.round(this.purchase!["amount"] * 100),
          participants: this.selected,
        },
        "Acquisto registrato e lista aggiornata.",
      )
    )
      this.purchase = null;
  }
}
