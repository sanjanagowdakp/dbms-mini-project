const form = document.querySelector('#expense-form');
const rows = document.querySelector('#expense-rows');
const loading = document.querySelector('#loading-state');
const empty = document.querySelector('#empty-state');
const toast = document.querySelector('#toast');
const search = document.querySelector('#search');
const expenseId = document.querySelector('#expense-id');
const submitButton = document.querySelector('#submit-button');
const cancelButton = document.querySelector('#cancel-edit');
let expenses = [];
let toastTimer;

const money = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });
const prettyDate = new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
document.querySelector('#current-month').textContent = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric' }).format(new Date());
document.querySelector('#date').value = new Date().toISOString().slice(0, 10);

async function request(url, options = {}) {
  const response = await fetch(url, options);
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || 'The request failed.');
  return data;
}

async function loadExpenses() {
  loading.classList.remove('hidden');
  empty.classList.add('hidden');
  try {
    expenses = await request('/api/expenses');
    render();
  } catch (error) {
    showToast(error.message, true);
    loading.textContent = 'Could not load expenses. Check the Java server and MySQL connection.';
  } finally {
    loading.classList.add('hidden');
  }
}

function render() {
  const query = search.value.trim().toLowerCase();
  const visible = expenses.filter((item) => (item.title + ' ' + item.category + ' ' + (item.notes || '')).toLowerCase().includes(query));
  rows.replaceChildren();
  for (const item of visible) rows.append(makeRow(item));
  empty.classList.toggle('hidden', visible.length !== 0);
  document.querySelector('#list-count').textContent = String(expenses.length);
  document.querySelector('#entry-count').textContent = String(expenses.length);
  document.querySelector('#footer-total').textContent = expenses.length + (expenses.length === 1 ? ' expense' : ' expenses');
  document.querySelector('#total-spent').textContent = money.format(expenses.reduce((sum, item) => sum + Number(item.amount), 0));
  const categories = new Map();
  for (const item of expenses) categories.set(item.category, (categories.get(item.category) || 0) + Number(item.amount));
  const top = [...categories.entries()].sort((a, b) => b[1] - a[1])[0];
  document.querySelector('#top-category').textContent = top ? top[0] : '—';
}

function makeRow(item) {
  const tr = document.createElement('tr');
  const nameCell = document.createElement('td');
  const name = document.createElement('div'); name.className = 'expense-name';
  const icon = document.createElement('span');
  icon.className = 'expense-icon ' + iconClass(item.category);
  icon.setAttribute('aria-hidden', 'true'); icon.textContent = iconText(item.category);
  const text = document.createElement('span');
  const title = document.createElement('strong'); title.textContent = item.title;
  text.append(title);
  if (item.notes) { const note = document.createElement('small'); note.textContent = item.notes; text.append(note); }
  name.append(icon, text); nameCell.append(name);

  const categoryCell = document.createElement('td');
  const pill = document.createElement('span'); pill.className = 'category-pill'; pill.textContent = item.category; categoryCell.append(pill);
  const dateCell = document.createElement('td'); dateCell.className = 'date-cell';
  dateCell.textContent = prettyDate.format(new Date(item.date + 'T00:00:00'));
  const amountCell = document.createElement('td'); amountCell.textContent = money.format(Number(item.amount));
  const actionsCell = document.createElement('td');
  const actions = document.createElement('div'); actions.className = 'actions';
  const edit = document.createElement('button');
  edit.className = 'icon-button'; edit.type = 'button'; edit.title = 'Edit expense';
  edit.setAttribute('aria-label', 'Edit ' + item.title); edit.textContent = '✎';
  edit.addEventListener('click', () => beginEdit(item));
  const remove = document.createElement('button');
  remove.className = 'icon-button delete'; remove.type = 'button'; remove.title = 'Delete expense';
  remove.setAttribute('aria-label', 'Delete ' + item.title); remove.textContent = '×';
  remove.addEventListener('click', () => deleteExpense(item));
  actions.append(edit, remove); actionsCell.append(actions);
  tr.append(nameCell, categoryCell, dateCell, amountCell, actionsCell);
  return tr;
}

function iconClass(category) { return category.toLowerCase().replace(/[^a-z]+/g, '-'); }
function iconText(category) {
  return ({ Food: '◒', Transport: '↗', Shopping: '◇', Bills: '▤', Health: '✚', Entertainment: '♫', Education: '▧', Other: '✳' })[category] || '✳';
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!form.reportValidity()) return;
  const values = new URLSearchParams(new FormData(form));
  const id = expenseId.value;
  submitButton.disabled = true;
  try {
    await request(id ? '/api/expenses/' + id : '/api/expenses', {
      method: id ? 'PUT' : 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
      body: values.toString(),
    });
    resetForm();
    await loadExpenses();
    showToast(id ? 'Expense updated.' : 'Expense added.');
  } catch (error) {
    showToast(error.message, true);
  } finally {
    submitButton.disabled = false;
  }
});

function beginEdit(item) {
  expenseId.value = item.id;
  form.elements.title.value = item.title;
  form.elements.category.value = item.category;
  form.elements.amount.value = item.amount;
  form.elements.date.value = item.date;
  form.elements.notes.value = item.notes || '';
  document.querySelector('#form-title').textContent = 'Edit expense';
  document.querySelector('#form-kicker').textContent = 'MAKE A CHANGE';
  submitButton.innerHTML = '<span>✓</span> Save changes';
  cancelButton.classList.remove('hidden');
  document.querySelector('.form-card').scrollIntoView({ behavior: 'smooth', block: 'start' });
  form.elements.title.focus({ preventScroll: true });
}

async function deleteExpense(item) {
  if (!window.confirm('Delete "' + item.title + '"? This cannot be undone.')) return;
  try {
    await request('/api/expenses/' + item.id, { method: 'DELETE' });
    if (expenseId.value === String(item.id)) resetForm();
    await loadExpenses();
    showToast('Expense deleted.');
  } catch (error) {
    showToast(error.message, true);
  }
}

function resetForm() {
  form.reset();
  expenseId.value = '';
  form.elements.date.value = new Date().toISOString().slice(0, 10);
  document.querySelector('#form-title').textContent = 'Add an expense';
  document.querySelector('#form-kicker').textContent = 'MAKE IT COUNT';
  submitButton.innerHTML = '<span>＋</span> Add expense';
  cancelButton.classList.add('hidden');
}

function showToast(message, isError = false) {
  clearTimeout(toastTimer);
  toast.textContent = message;
  toast.classList.toggle('error', isError);
  toast.classList.add('show');
  toastTimer = setTimeout(() => toast.classList.remove('show'), 3000);
}

cancelButton.addEventListener('click', resetForm);
search.addEventListener('input', render);
loadExpenses();