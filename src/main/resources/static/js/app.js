/* ==========================================================================
   SlotSync - Application Frontend Logic
   ========================================================================== */

const API_BASE = 'http://localhost:8088/api/v1';

// Global State
let currentUser = null;
let currentAuthMode = 'login'; // 'login' or 'register'
let countdownIntervals = {};

// Initialize application on DOM load
document.addEventListener('DOMContentLoaded', () => {
  initTabs();
  checkAuth();
  loadResources();
  loadSlots();
});

// Toast System
function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  
  const icon = type === 'success' ? 'fa-circle-check' : (type === 'error' ? 'fa-circle-exclamation' : 'fa-circle-info');
  toast.innerHTML = `<i class="fa-solid ${icon}"></i> <span>${message}</span>`;
  
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}

// Tab Switching Logic
function initTabs() {
  const tabBtns = document.querySelectorAll('.tab-btn');
  tabBtns.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetTabId = btn.getAttribute('data-tab');
      
      tabBtns.forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));
      
      btn.classList.add('active');
      const targetPanel = document.getElementById(targetTabId);
      if (targetPanel) targetPanel.classList.add('active');

      // Refresh tab data
      if (targetTabId === 'slotsTab') loadSlots();
      if (targetTabId === 'resourcesTab') loadResources();
      if (targetTabId === 'myBookingsTab') loadMyBookings();
    });
  });
}

// Helper: HTTP Fetch Wrapper with Auth Headers
async function apiRequest(endpoint, method = 'GET', body = null) {
  const headers = { 'Content-Type': 'application/json' };
  const token = localStorage.getItem('slotsync_token');
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const config = { method, headers };
  if (body) config.body = JSON.stringify(body);

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, config);
    if (res.status === 204) return true;
    
    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.message || `HTTP Error ${res.status}`);
    }
    return data;
  } catch (err) {
    console.error('API Error:', err);
    throw err;
  }
}

// Authentication Check & UI Update
function checkAuth() {
  const savedUser = localStorage.getItem('slotsync_user');
  const token = localStorage.getItem('slotsync_token');

  if (savedUser && token) {
    currentUser = JSON.parse(savedUser);
    renderUserNav();
  } else {
    currentUser = null;
    renderUserNav();
  }
}

function renderUserNav() {
  const navSection = document.getElementById('navUserSection');
  const adminBtns = document.querySelectorAll('.admin-only');

  if (currentUser) {
    const isAdmin = currentUser.role === 'ADMIN';
    
    // Show / Hide Admin elements
    adminBtns.forEach(el => {
      el.style.display = isAdmin ? 'inline-flex' : 'none';
    });

    navSection.innerHTML = `
      <div class="user-badge">
        <i class="fa-solid fa-user"></i>
        <span>${currentUser.name}</span>
        <span class="role-tag ${currentUser.role.toLowerCase()}">${currentUser.role}</span>
      </div>
      <button class="btn btn-secondary btn-sm" onclick="handleLogout()">
        <i class="fa-solid fa-right-from-bracket"></i> Logout
      </button>
    `;
  } else {
    adminBtns.forEach(el => el.style.display = 'none');
    navSection.innerHTML = `
      <button class="btn btn-primary" onclick="openAuthModal('login')">
        <i class="fa-solid fa-right-to-bracket"></i> Login / Register
      </button>
    `;
  }
}

function handleLogout() {
  localStorage.removeItem('slotsync_token');
  localStorage.removeItem('slotsync_user');
  currentUser = null;
  renderUserNav();
  showToast('Logged out successfully', 'info');
  loadSlots();
}

// Auth Modal Management
function openAuthModal(mode = 'login') {
  currentAuthMode = mode;
  const modal = document.getElementById('authModal');
  modal.classList.add('active');
  updateAuthModalUI();
}

function closeAuthModal() {
  document.getElementById('authModal').classList.remove('active');
}

function toggleAuthMode(e) {
  e.preventDefault();
  currentAuthMode = currentAuthMode === 'login' ? 'register' : 'login';
  updateAuthModalUI();
}

function updateAuthModalUI() {
  const isReg = currentAuthMode === 'register';
  document.getElementById('authModalTitle').innerText = isReg ? 'Create Account' : 'Welcome Back';
  document.getElementById('authModalSubtitle').innerText = isReg ? 'Register as User or Admin to manage bookings' : 'Login to access your bookings';
  document.getElementById('nameGroup').style.display = isReg ? 'block' : 'none';
  document.getElementById('roleGroup').style.display = isReg ? 'block' : 'none';
  document.getElementById('authSubmitBtn').innerHTML = isReg ? '<i class="fa-solid fa-user-plus"></i> Register' : '<i class="fa-solid fa-right-to-bracket"></i> Login';
  document.getElementById('authToggleText').innerText = isReg ? 'Already have an account?' : "Don't have an account?";
  document.getElementById('authToggleLink').innerText = isReg ? 'Login' : 'Register Now';
}

async function handleAuthSubmit(e) {
  e.preventDefault();
  const email = document.getElementById('authEmail').value;
  const password = document.getElementById('authPassword').value;

  try {
    let response;
    if (currentAuthMode === 'register') {
      const name = document.getElementById('authName').value;
      const role = document.getElementById('authRole').value;
      response = await apiRequest('/auth/register', 'POST', { name, email, password, role });
      showToast('Registration successful!', 'success');
    } else {
      response = await apiRequest('/auth/login', 'POST', { email, password });
      showToast('Login successful!', 'success');
    }

    localStorage.setItem('slotsync_token', response.token);
    localStorage.setItem('slotsync_user', JSON.stringify({
      userId: response.userId,
      name: response.name,
      email: response.email,
      role: response.role
    }));

    closeAuthModal();
    checkAuth();
    loadSlots();
    loadResources();
  } catch (err) {
    showToast(err.message || 'Authentication failed', 'error');
  }
}

// LOAD RESOURCES
async function loadResources() {
  try {
    const resources = await apiRequest('/resources?activeOnly=true');
    renderResources(resources);
    populateResourceSelects(resources);
  } catch (err) {
    document.getElementById('resourcesGrid').innerHTML = `<div class="empty-state"><i class="fa-solid fa-triangle-exclamation"></i><p>${err.message}</p></div>`;
  }
}

function renderResources(resources) {
  const container = document.getElementById('resourcesGrid');
  if (!resources || resources.length === 0) {
    container.innerHTML = '<div class="empty-state"><i class="fa-solid fa-box-open"></i><p>No active resources found</p></div>';
    return;
  }

  container.innerHTML = resources.map(res => `
    <div class="card">
      <div class="card-header">
        <div>
          <div class="card-title">${res.name}</div>
          <div class="card-subtitle">ID: ${res.id} • ${res.type}</div>
        </div>
        <span class="badge badge-available">ACTIVE</span>
      </div>
      <div class="card-body">
        <div class="info-row">
          <i class="fa-solid fa-layer-group info-icon"></i>
          <span>Type: <strong>${res.type}</strong></span>
        </div>
      </div>
    </div>
  `).join('');
}

function populateResourceSelects(resources) {
  const filterSelect = document.getElementById('filterResource');
  const adminSelect = document.getElementById('slotResourceId');

  const optionsHTML = '<option value="">All Resources</option>' + 
    resources.map(r => `<option value="${r.id}">${r.name} (${r.type})</option>`).join('');

  filterSelect.innerHTML = optionsHTML;
  
  if (adminSelect) {
    adminSelect.innerHTML = '<option value="">Select Resource...</option>' + 
      resources.map(r => `<option value="${r.id}">${r.name}</option>`).join('');
  }
}

// LOAD SLOTS
async function loadSlots() {
  const resourceId = document.getElementById('filterResource').value;
  const status = document.getElementById('filterStatus').value;

  let query = [];
  if (resourceId) query.push(`resourceId=${resourceId}`);
  if (status) query.push(`status=${status}`);
  const queryString = query.length ? `?${query.join('&')}` : '';

  try {
    const slots = await apiRequest(`/slots${queryString}`);
    renderSlots(slots);
  } catch (err) {
    document.getElementById('slotsGrid').innerHTML = `<div class="empty-state"><i class="fa-solid fa-triangle-exclamation"></i><p>${err.message}</p></div>`;
  }
}

function renderSlots(slots) {
  const container = document.getElementById('slotsGrid');
  if (!slots || slots.length === 0) {
    container.innerHTML = '<div class="empty-state"><i class="fa-solid fa-calendar-xmark"></i><p>No time slots matching filters</p></div>';
    return;
  }

  container.innerHTML = slots.map(slot => {
    const isAvailable = slot.status === 'AVAILABLE';
    const startDate = new Date(slot.startTime).toLocaleString();
    const endDate = new Date(slot.endTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    return `
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">${slot.resourceName}</div>
            <div class="card-subtitle">Slot #${slot.id}</div>
          </div>
          <span class="badge badge-${slot.status.toLowerCase()}">${slot.status}</span>
        </div>
        <div class="card-body">
          <div class="info-row">
            <i class="fa-solid fa-clock info-icon"></i>
            <span>${startDate} — ${endDate}</span>
          </div>
        </div>
        ${isAvailable ? `
          <button class="btn btn-primary" style="width: 100%;" onclick="holdSlot(${slot.id})">
            <i class="fa-solid fa-hand"></i> Hold Slot (15 min)
          </button>
        ` : `
          <button class="btn btn-secondary" style="width: 100%;" disabled>
            ${slot.status}
          </button>
        `}
      </div>
    `;
  }).join('');
}

// HOLD SLOT
async function holdSlot(slotId) {
  if (!currentUser) {
    openAuthModal('login');
    showToast('Please login to hold a slot', 'info');
    return;
  }

  // Generate unique idempotency key
  const idempotencyKey = `hold_${slotId}_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;

  try {
    const booking = await apiRequest('/bookings/hold', 'POST', {
      slotId: slotId,
      idempotencyKey: idempotencyKey,
      holdDurationMinutes: 15
    });

    showToast(`Slot #${slotId} placed on hold!`, 'success');
    loadSlots();
    // Switch to My Bookings tab
    document.querySelector('[data-tab="myBookingsTab"]').click();
  } catch (err) {
    showToast(err.message || 'Failed to hold slot', 'error');
  }
}

// LOAD MY BOOKINGS
async function loadMyBookings() {
  if (!currentUser) {
    document.getElementById('myBookingsGrid').innerHTML = `
      <div class="empty-state">
        <i class="fa-solid fa-lock"></i>
        <p>Please login to view your active bookings</p>
        <button class="btn btn-primary" style="margin-top: 12px;" onclick="openAuthModal('login')">Login Now</button>
      </div>
    `;
    return;
  }

  try {
    const bookings = await apiRequest('/bookings/my-bookings');
    renderMyBookings(bookings);
  } catch (err) {
    document.getElementById('myBookingsGrid').innerHTML = `<div class="empty-state"><i class="fa-solid fa-triangle-exclamation"></i><p>${err.message}</p></div>`;
  }
}

function renderMyBookings(bookings) {
  const container = document.getElementById('myBookingsGrid');
  
  // Clear any existing countdown intervals
  Object.values(countdownIntervals).forEach(clearInterval);
  countdownIntervals = {};

  if (!bookings || bookings.length === 0) {
    container.innerHTML = '<div class="empty-state"><i class="fa-solid fa-ticket-simple"></i><p>You have no active bookings or holds</p></div>';
    return;
  }

  container.innerHTML = bookings.map(b => {
    const isHeld = b.status === 'HELD';
    const isConfirmed = b.status === 'CONFIRMED';
    const startTime = new Date(b.slotStartTime).toLocaleString();
    const endTime = new Date(b.slotEndTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    return `
      <div class="card">
        <div class="card-header">
          <div>
            <div class="card-title">${b.resourceName}</div>
            <div class="card-subtitle">Booking #${b.id} • Key: ${b.idempotencyKey.substring(0, 16)}...</div>
          </div>
          <span class="badge badge-${b.status.toLowerCase()}">${b.status}</span>
        </div>
        <div class="card-body">
          <div class="info-row">
            <i class="fa-solid fa-calendar info-icon"></i>
            <span>${startTime} — ${endTime}</span>
          </div>
          ${isHeld && b.holdExpiresAt ? `
            <div class="info-row">
              <i class="fa-solid fa-stopwatch info-icon" style="color: var(--warning);"></i>
              <span class="countdown-box" id="timer-${b.id}">Calculating...</span>
            </div>
          ` : ''}
        </div>
        <div style="display: flex; gap: 10px;">
          ${isHeld ? `
            <button class="btn btn-success" style="flex: 1;" onclick="confirmBooking('${b.idempotencyKey}')">
              <i class="fa-solid fa-check"></i> Confirm
            </button>
          ` : ''}
          ${isHeld || isConfirmed ? `
            <button class="btn btn-danger btn-sm" onclick="cancelBooking(${b.id})">
              <i class="fa-solid fa-xmark"></i> Cancel
            </button>
          ` : ''}
        </div>
      </div>
    `;
  }).join('');

  // Start live timers for HELD items
  bookings.filter(b => b.status === 'HELD' && b.holdExpiresAt).forEach(b => {
    startCountdown(b.id, b.holdExpiresAt);
  });
}

function startCountdown(bookingId, expireIsoString) {
  const timerEl = document.getElementById(`timer-${bookingId}`);
  if (!timerEl) return;

  const expireTime = new Date(expireIsoString).getTime();

  function update() {
    const now = new Date().getTime();
    const distance = expireTime - now;

    if (distance <= 0) {
      timerEl.innerText = 'EXPIRED';
      clearInterval(countdownIntervals[bookingId]);
      return;
    }

    const minutes = Math.floor((distance % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((distance % (1000 * 60)) / 1000);

    timerEl.innerText = `${minutes}m ${seconds < 10 ? '0' : ''}${seconds}s remaining`;
  }

  update();
  countdownIntervals[bookingId] = setInterval(update, 1000);
}

// CONFIRM BOOKING
async function confirmBooking(idempotencyKey) {
  try {
    await apiRequest('/bookings/confirm', 'POST', { idempotencyKey });
    showToast('Booking confirmed successfully!', 'success');
    loadMyBookings();
  } catch (err) {
    showToast(err.message || 'Failed to confirm booking', 'error');
  }
}

// CANCEL BOOKING
async function cancelBooking(bookingId) {
  if (!confirm('Are you sure you want to cancel this booking?')) return;

  try {
    await apiRequest(`/bookings/${bookingId}/cancel`, 'POST');
    showToast('Booking cancelled', 'info');
    loadMyBookings();
    loadSlots();
  } catch (err) {
    showToast(err.message || 'Failed to cancel booking', 'error');
  }
}

// ADMIN FORM HANDLERS
async function handleCreateResource(e) {
  e.preventDefault();
  const name = document.getElementById('resName').value;
  const type = document.getElementById('resType').value;

  try {
    await apiRequest('/resources', 'POST', { name, type, active: true });
    showToast(`Resource "${name}" created!`, 'success');
    document.getElementById('createResourceForm').reset();
    loadResources();
  } catch (err) {
    showToast(err.message || 'Failed to create resource', 'error');
  }
}

async function handleCreateSlot(e) {
  e.preventDefault();
  const resourceId = document.getElementById('slotResourceId').value;
  const startTime = document.getElementById('slotStartTime').value;
  const endTime = document.getElementById('slotEndTime').value;

  try {
    await apiRequest('/slots', 'POST', { resourceId: parseInt(resourceId), startTime, endTime });
    showToast('Time slot published successfully!', 'success');
    document.getElementById('createSlotForm').reset();
    loadSlots();
  } catch (err) {
    showToast(err.message || 'Failed to create slot', 'error');
  }
}
