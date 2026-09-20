const SupabaseConfig = {
  URL: 'https://tarnljxakeomtcjleofj.supabase.co',
  ANON_KEY: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRhcm5sanhha2VvbXRjamxlb2ZqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1OTY3MjksImV4cCI6MjEwNDE3MjcyOX0.EjLS31HVNjrM0bk9dmTRi96Vay33oOWP6H_56_BkzIg',
  SERVICE_KEY: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRhcm5sanhha2VvbXRjamxlb2ZqIiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4ODU5NjcyOSwiZXhwIjoyMTA0MTcyNzI5fQ.Gpxx8M04SrccOD7EFFBIX3TPjxqjwQ3vG6Re4X_vI40',

  ADMIN_EMAIL: 'admin@zorostore.com',
  ADMIN_PASSWORD: 'admin123',
  ADMIN_NAME: 'Admin',
  ADMIN_ROLE: 'admin',

  ACCOUNTS_KEY: 'zoro_supabase_accounts',
  ACTIVE_ACCOUNT_KEY: 'zoro_active_account',

  ALLOWED_EMAIL_DOMAINS: [
    'gmail.com', 'outlook.com', 'hotmail.com', 'live.com',
    'yahoo.com', 'yahoo.co.in', 'yahoo.co.uk',
    'qq.com', 'foxmail.com',
    'icloud.com', 'me.com', 'mac.com',
    'protonmail.com', 'proton.me',
    'zoho.com', 'yandex.com', 'mail.ru',
    'aol.com', 'gmx.com', 'fastmail.com',
    'pm.me', 'tutanota.com', 'tutamail.com',
    'email.com', 'real-email.com',
    'zorostore.com'
  ],

  DISPOSABLE_DOMAINS: [
    'tempmail.com', 'throwaway.email', 'guerrillamail.com',
    'mailinator.com', 'yopmail.com', 'trashmail.com',
    'fakeinbox.com', 'sharklasers.com', 'guerrillamailblock.com',
    'grr.la', 'dispostable.com', 'maildrop.cc',
    'temp-mail.org', 'tempail.com', 'tempr.email',
    '10minutemail.com', 'minuteinbox.com', 'mohmal.com',
    'getnada.com', 'emailondeck.com', '33mail.com',
    'mytemp.email', 'burnermail.io', 'harakirimail.com',
    'tmail.io', 'tmpmail.net', 'tmpmail.org'
  ],

  getClient() {
    return supabase.createClient(this.URL, this.ANON_KEY);
  },

  getServiceClient() {
    return supabase.createClient(this.URL, this.SERVICE_KEY);
  },

  getAccounts() {
    try { return JSON.parse(localStorage.getItem(this.ACCOUNTS_KEY)) || []; }
    catch { return []; }
  },

  saveAccounts(accounts) {
    localStorage.setItem(this.ACCOUNTS_KEY, JSON.stringify(accounts));
  },

  getActiveAccountId() {
    return localStorage.getItem(this.ACTIVE_ACCOUNT_KEY);
  },

  setActiveAccountId(id) {
    localStorage.setItem(this.ACTIVE_ACCOUNT_KEY, id);
  },

  getActiveAccount() {
    const accounts = this.getAccounts();
    const activeId = this.getActiveAccountId();
    return accounts.find(a => a.id === activeId && a.isActive) || accounts.find(a => a.isActive) || null;
  },

  async syncToSupabase() {
    try {
      const client = this.getServiceClient();
      const accounts = this.getAccounts();
      const activeId = this.getActiveAccountId();

      await client.from('supabase_accounts').delete().neq('id', '__none__');

      if (!accounts.length) return;

      const rows = accounts.map(a => ({
        id: a.id,
        name: a.name || '',
        email: a.email || '',
        sup_url: a.url || '',
        sup_anon_key: a.anonKey || '',
        sup_service_key: a.serviceKey || '',
        max_users: a.maxUsers || 100,
        current_users: a.currentUsers || 0,
        is_active: a.id === activeId && a.isActive,
        updated_at: new Date().toISOString()
      }));

      const { error } = await client.from('supabase_accounts').insert(rows);
      if (error) console.error('Sync failed:', error.message);
    } catch (e) { console.error('Sync error:', e.message); }
  },

  async addAccount({ name, email, url, anonKey, serviceKey, maxUsers }) {
    const accounts = this.getAccounts();
    const id = 'acc_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6);
    const account = { id, name, email: email || '', url, anonKey, serviceKey, maxUsers: maxUsers || 100, currentUsers: 0, isActive: false, createdAt: new Date().toISOString() };
    accounts.push(account);
    this.saveAccounts(accounts);
    this.syncToSupabase();
    return account;
  },

  updateAccount(id, updates) {
    const accounts = this.getAccounts();
    const idx = accounts.findIndex(a => a.id === id);
    if (idx === -1) return null;
    accounts[idx] = { ...accounts[idx], ...updates };
    this.saveAccounts(accounts);
    this.syncToSupabase();
    return accounts[idx];
  },

  deleteAccount(id) {
    let accounts = this.getAccounts();
    accounts = accounts.filter(a => a.id !== id);
    this.saveAccounts(accounts);
    this.syncToSupabase();
    if (this.getActiveAccountId() === id) {
      const next = accounts.find(a => a.isActive);
      this.setActiveAccountId(next ? next.id : '');
    }
  },

  toggleAccount(id) {
    const accounts = this.getAccounts();
    const acc = accounts.find(a => a.id === id);
    if (!acc) return null;
    if (acc.isActive) {
      acc.isActive = false;
      this.saveAccounts(accounts);
      this.syncToSupabase();
      if (this.getActiveAccountId() === id) {
        const next = accounts.find(a => a.isActive && a.id !== id);
        this.setActiveAccountId(next ? next.id : '');
      }
      return acc;
    }
    if (acc.currentUsers >= acc.maxUsers) return { error: 'MAX_LIMIT', account: acc };
    acc.isActive = true;
    this.saveAccounts(accounts);
    this.syncToSupabase();
    this.setActiveAccountId(id);
    return acc;
  },

  incrementUsers(id) {
    const accounts = this.getAccounts();
    const acc = accounts.find(a => a.id === id);
    if (!acc) return;
    acc.currentUsers = (acc.currentUsers || 0) + 1;
    this.saveAccounts(accounts);
    this.syncToSupabase();
    if (acc.currentUsers >= acc.maxUsers && acc.isActive) {
      acc.isActive = false;
      this.saveAccounts(accounts);
      const freeAccount = accounts.find(a => a.id !== id && (a.currentUsers || 0) < a.maxUsers);
      if (freeAccount) {
        freeAccount.isActive = true;
        this.saveAccounts(accounts);
        this.setActiveAccountId(freeAccount.id);
      } else {
        this.setActiveAccountId('');
      }
    }
  },

  validateEmail(email) {
    if (!email || typeof email !== 'string') return { valid: false, error: 'Email is required' };
    email = email.trim().toLowerCase();
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!emailRegex.test(email)) return { valid: false, error: 'Invalid email format' };
    if (email.includes('+')) return { valid: false, error: '"+" symbol is not allowed in email' };
    const domain = email.split('@')[1];
    if (this.DISPOSABLE_DOMAINS.includes(domain)) return { valid: false, error: 'Disposable/temp emails are not allowed' };
    if (!this.ALLOWED_EMAIL_DOMAINS.includes(domain)) return { valid: false, error: `Email domain "@${domain}" is not supported. Use Gmail, Outlook, Yahoo, QQ, etc.` };
    return { valid: true };
  }
};
