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
      if (targetTabId === 'aiTab') refreshAiHubData();
      if (targetTabId === 'adminTab' && currentUser && currentUser.role === 'ADMIN') loadAdminAiAudit();
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
      let errorMsg = data.message || `HTTP Error ${res.status}`;
      if (data.errors && Array.isArray(data.errors) && data.errors.length > 0) {
        errorMsg = data.errors.join(' | ');
      }
      throw new Error(errorMsg);
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
  
  const nameGroup = document.getElementById('nameGroup');
  const roleGroup = document.getElementById('roleGroup');
  const authNameInput = document.getElementById('authName');
  const authPassInput = document.getElementById('authPassword');

  nameGroup.style.display = isReg ? 'block' : 'none';
  roleGroup.style.display = isReg ? 'block' : 'none';
  authNameInput.required = isReg;
  
  if (isReg) {
    authPassInput.setAttribute('minlength', '6');
  } else {
    authPassInput.removeAttribute('minlength');
  }

  document.getElementById('authSubmitBtn').innerHTML = isReg ? '<i class="fa-solid fa-user-plus"></i> Register' : '<i class="fa-solid fa-right-to-bracket"></i> Login';
  document.getElementById('authToggleText').innerText = isReg ? 'Already have an account?' : "Don't have an account?";
  document.getElementById('authToggleLink').innerText = isReg ? 'Login' : 'Register Now';
}

async function handleAuthSubmit(e) {
  e.preventDefault();
  const email = document.getElementById('authEmail').value.trim();
  const password = document.getElementById('authPassword').value;

  try {
    let response;
    if (currentAuthMode === 'register') {
      const name = document.getElementById('authName').value.trim();
      const role = document.getElementById('authRole').value;

      if (!name) {
        showToast('Please enter your name', 'error');
        return;
      }

      response = await apiRequest('/auth/register', 'POST', { name, email, password, role });
      showToast('Registration successful! Welcome to SlotSync', 'success');
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

    document.getElementById('authForm').reset();
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
          <div style="display: flex; gap: 6px; align-items: center;">
            <span class="badge badge-dynamic-ttl" title="Real-time contention monitoring active">
              <i class="fa-solid fa-bolt"></i> AI TTL
            </span>
            <span class="badge badge-${slot.status.toLowerCase()}">${slot.status}</span>
          </div>
        </div>
        <div class="card-body">
          <div class="info-row">
            <i class="fa-solid fa-clock info-icon"></i>
            <span>${startDate} — ${endDate}</span>
          </div>
        </div>
        ${isAvailable ? `
          <button class="btn btn-primary" style="width: 100%;" onclick="holdSlot(${slot.id})">
            <i class="fa-solid fa-hand"></i> Hold Slot (Dynamic AI TTL)
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
    // Omitting holdDurationMinutes instructs the AI engine to compute the dynamic TTL
    const booking = await apiRequest('/bookings/hold', 'POST', {
      slotId: slotId,
      idempotencyKey: idempotencyKey
    });

    showToast(`Slot #${slotId} placed on hold with Dynamic AI TTL!`, 'success');
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
          ${isConfirmed ? `
            <div class="info-row" style="margin-top: 4px;">
              <i class="fa-solid fa-shield-halved info-icon" style="color: #34d399;"></i>
              <span id="ai-risk-${b.id}" class="badge badge-risk-low" style="font-size: 0.72rem;">
                <i class="fa-solid fa-spinner fa-spin"></i> Analyzing Attendance Risk...
              </span>
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

  // Load AI risk predictions for CONFIRMED items
  bookings.filter(b => b.status === 'CONFIRMED').forEach(b => {
    loadBookingNoShowRisk(b.id);
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

// ==========================================================================
// AI Suite & Copilot Frontend Handlers
// ==========================================================================

function openAiCopilotTab() {
  const aiTabBtn = document.getElementById('aiTabBtn');
  if (aiTabBtn) {
    aiTabBtn.click();
    const input = document.getElementById('aiChatInput');
    if (input) input.focus();
  }
}

async function sendAiQuickPrompt(prompt) {
  const input = document.getElementById('aiChatInput');
  if (input) {
    input.value = prompt;
    await sendAiMessage();
  }
}

async function sendAiMessage() {
  const input = document.getElementById('aiChatInput');
  if (!input) return;
  const message = input.value.trim();
  if (!message) return;

  if (!currentUser) {
    openAuthModal('login');
    showToast('Please login to chat with the AI Copilot', 'info');
    return;
  }

  input.value = '';
  const feed = document.getElementById('aiChatFeed');

  // Render User Message
  const userMsgEl = document.createElement('div');
  userMsgEl.className = 'chat-msg user';
  userMsgEl.innerHTML = `
    <div class="chat-avatar"><i class="fa-solid fa-user"></i></div>
    <div class="chat-bubble">${escapeHtml(message)}</div>
  `;
  feed.appendChild(userMsgEl);

  // Render Typing Indicator
  const typingEl = document.createElement('div');
  typingEl.className = 'chat-msg assistant typing-placeholder';
  typingEl.innerHTML = `
    <div class="chat-avatar"><i class="fa-solid fa-robot"></i></div>
    <div class="chat-bubble">
      <div class="typing-dots">
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
      </div>
    </div>
  `;
  feed.appendChild(typingEl);
  feed.scrollTop = feed.scrollHeight;

  try {
    const res = await apiRequest('/ai/copilot/chat', 'POST', { message });
    typingEl.remove();

    // Render Assistant Reply
    const assistantEl = document.createElement('div');
    assistantEl.className = 'chat-msg assistant';

    // Format markdown bolding and line breaks
    let formattedReply = formatAiReply(res.reply);

    let actionsHtml = '';
    if (res.actions && res.actions.length > 0) {
      actionsHtml = `<div class="chat-actions-container">` +
        res.actions.map(a => `
          <button class="chat-action-btn" onclick="handleAiAction('${a.actionType}', '${a.payload}')">
            ${escapeHtml(a.label)}
          </button>
        `).join('') +
        `</div>`;
    }

    assistantEl.innerHTML = `
      <div class="chat-avatar"><i class="fa-solid fa-robot"></i></div>
      <div class="chat-bubble">
        <div>${formattedReply}</div>
        ${actionsHtml}
      </div>
    `;
    feed.appendChild(assistantEl);
    feed.scrollTop = feed.scrollHeight;

    // Trigger state refreshes if actions modified slots or bookings
    if (res.detectedIntent === 'HOLD_SLOT_SUCCESS' || res.detectedIntent === 'CONFIRM_SUCCESS' || res.detectedIntent === 'CANCEL_SUCCESS') {
      loadSlots();
      loadMyBookings();
      refreshAiHubData();
    }
  } catch (err) {
    typingEl.remove();
    const errorEl = document.createElement('div');
    errorEl.className = 'chat-msg assistant';
    errorEl.innerHTML = `
      <div class="chat-avatar" style="background: var(--danger);"><i class="fa-solid fa-triangle-exclamation"></i></div>
      <div class="chat-bubble" style="border-color: rgba(239, 68, 68, 0.4);">
        ❌ ${escapeHtml(err.message || 'AI request failed')}
      </div>
    `;
    feed.appendChild(errorEl);
    feed.scrollTop = feed.scrollHeight;
  }
}

async function handleAiAction(actionType, payload) {
  if (actionType === 'HOLD_SLOT') {
    await holdSlot(parseInt(payload));
  } else if (actionType === 'CONFIRM_BOOKING') {
    await confirmBooking(payload);
  } else if (actionType === 'CANCEL_BOOKING') {
    await cancelBooking(parseInt(payload));
  } else if (actionType === 'BROWSE_SLOTS') {
    document.querySelector('[data-tab="slotsTab"]').click();
  } else if (actionType === 'MY_BOOKINGS') {
    document.querySelector('[data-tab="myBookingsTab"]').click();
  } else if (actionType === 'RECOMMEND') {
    await sendAiQuickPrompt('Recommend best slot to minimize calendar gaps');
  } else if (actionType === 'EXPLAIN_TTL') {
    await sendAiQuickPrompt('Why is dynamic TTL used and how does lock contention work?');
  } else if (actionType === 'VIEW_CONTENTION') {
    openAiCopilotTab();
  }
}

function formatAiReply(text) {
  if (!text) return '';
  return text
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\n/g, '<br>');
}

function escapeHtml(str) {
  if (!str) return '';
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

async function refreshAiHubData() {
  // 1. Load System Contention Analytics
  try {
    const analytics = await apiRequest('/ai/contention/analytics');
    const scoreEl = document.getElementById('aiContentionScore');
    const barEl = document.getElementById('aiContentionBar');
    const adviceEl = document.getElementById('aiTuningAdvice');
    const pillEl = document.getElementById('aiContentionPill');

    if (scoreEl && barEl) {
      scoreEl.innerText = `${analytics.averageContentionScore} / 1.00`;
      const pct = Math.min(100, Math.max(10, Math.round(analytics.averageContentionScore * 100)));
      barEl.style.width = `${pct}%`;

      if (analytics.averageContentionScore >= 0.5) {
        barEl.style.background = '#ef4444';
        pillEl.innerText = 'HIGH COLLISION';
        pillEl.style.borderColor = '#ef4444';
        pillEl.style.color = '#f87171';
      } else {
        barEl.style.background = '#22d3ee';
        pillEl.innerText = 'HEALTHY';
        pillEl.style.borderColor = 'rgba(6, 182, 212, 0.35)';
        pillEl.style.color = '#22d3ee';
      }
    }

    if (adviceEl && analytics.autoTuningRecommendations && analytics.autoTuningRecommendations.length > 0) {
      adviceEl.innerHTML = analytics.autoTuningRecommendations.map(r => `• ${escapeHtml(r)}`).join('<br>');
    }
  } catch (err) {
    console.error('Failed to load contention analytics', err);
  }

  // 2. Load Top Recommendations
  try {
    const recs = await apiRequest('/ai/recommendations?durationMinutes=60');
    const container = document.getElementById('aiTopPicksContainer');
    if (container) {
      if (!recs || recs.length === 0) {
        container.innerHTML = '<div style="font-size: 0.84rem; color: var(--text-muted);">No upcoming slots available for optimization.</div>';
      } else {
        container.innerHTML = recs.slice(0, 3).map(r => `
          <div style="padding: 10px; background: rgba(255,255,255,0.03); border-radius: 8px; margin-bottom: 8px; border: 1px solid var(--glass-border);">
            <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px;">
              <strong style="font-size: 0.88rem;">${escapeHtml(r.resourceName)} (Slot #${r.slotId})</strong>
              <span class="badge badge-optimal">${r.efficiencyScore}% Match</span>
            </div>
            <div style="font-size: 0.78rem; color: var(--text-muted); margin-bottom: 8px;">
              ${new Date(r.startTime).toLocaleString([], { dateStyle: 'short', timeStyle: 'short' })} — ${escapeHtml(r.reason)}
            </div>
            <button class="btn btn-primary btn-sm" style="width: 100%; font-size: 0.78rem; padding: 4px 8px;" onclick="holdSlot(${r.slotId})">
              <i class="fa-solid fa-bolt"></i> Hold with Dynamic TTL
            </button>
          </div>
        `).join('');
      }
    }
  } catch (err) {
    console.error('Failed to load recommendations', err);
  }

  // 3. Load Current User Shield Status
  if (currentUser) {
    try {
      const risk = await apiRequest('/ai/abuse/my-status');
      const scoreEl = document.getElementById('aiUserRiskScore');
      const holdsEl = document.getElementById('aiUserActiveHolds');
      const badgeEl = document.getElementById('aiUserShieldBadge');

      if (scoreEl) scoreEl.innerHTML = `User Risk Score: <strong>${risk.riskScore} (${risk.riskLevel})</strong>`;
      if (holdsEl) holdsEl.innerHTML = `Concurrent Holds: <strong>${risk.activeHoldsCount} / 3 Allowed</strong>`;
      if (badgeEl) {
        if (risk.flagged) {
          badgeEl.innerText = 'THROTTLED';
          badgeEl.style.background = 'rgba(239,68,68,0.2)';
          badgeEl.style.color = '#f87171';
        } else {
          badgeEl.innerText = 'PROTECTED';
          badgeEl.style.background = 'rgba(16,185,129,0.15)';
          badgeEl.style.color = '#34d399';
        }
      }
    } catch (err) {
      console.error('Failed to load user shield status', err);
    }
  }
}

async function showAiRecommendations() {
  openAiCopilotTab();
  await sendAiQuickPrompt('Recommend best slot to minimize calendar gaps');
}

async function loadBookingNoShowRisk(bookingId) {
  try {
    const risk = await apiRequest(`/ai/no-show-risk/${bookingId}`);
    const badge = document.getElementById(`ai-risk-${bookingId}`);
    if (badge) {
      const pct = Math.round(risk.noShowProbability * 100);
      if (risk.riskCategory === 'HIGH_RISK') {
        badge.className = 'badge badge-risk-high';
        badge.innerHTML = `<i class="fa-solid fa-triangle-exclamation"></i> High No-Show Risk (${pct}%)`;
      } else {
        badge.className = 'badge badge-risk-low';
        badge.innerHTML = `<i class="fa-solid fa-shield-check"></i> Attendance Confidence (${100 - pct}%)`;
      }
      badge.title = risk.riskFactors ? risk.riskFactors.join(' | ') : risk.recommendation;
    }
  } catch (err) {
    console.error(`Failed to load risk for booking #${bookingId}`, err);
  }
}

async function loadAdminAiAudit() {
  try {
    const report = await apiRequest('/ai/abuse/audit');
    const tbody = document.getElementById('adminAiAuditTableBody');
    if (!tbody) return;

    if (!report || report.length === 0) {
      tbody.innerHTML = '<tr><td colspan="8" style="text-align: center; color: var(--text-muted); padding: 18px;">No users currently registered</td></tr>';
      return;
    }

    tbody.innerHTML = report.map(u => `
      <tr>
        <td>#${u.userId}</td>
        <td><strong>${escapeHtml(u.email)}</strong></td>
        <td>${u.riskScore}</td>
        <td>
          <span class="badge ${u.riskLevel === 'HIGH_RISK' ? 'badge-risk-high' : 'badge-risk-low'}">
            ${u.riskLevel}
          </span>
        </td>
        <td>${u.activeHoldsCount}</td>
        <td>${u.recentHoldsCount} / min</td>
        <td>${Math.round(u.abandonmentRate * 100)}%</td>
        <td>
          ${u.flagged 
            ? '<span style="color: var(--danger); font-weight: 700;"><i class="fa-solid fa-ban"></i> THROTTLED</span>' 
            : '<span style="color: var(--success); font-weight: 600;"><i class="fa-solid fa-check"></i> ACTIVE</span>'}
        </td>
      </tr>
    `).join('');
  } catch (err) {
    const tbody = document.getElementById('adminAiAuditTableBody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="8" style="text-align: center; color: var(--danger); padding: 18px;">${escapeHtml(err.message)}</td></tr>`;
    }
  }
}
