// Основные утилиты
document.addEventListener('DOMContentLoaded', function() {
    // Инициализация всех компонентов
    initForms();
    initSidebar();
    initCharts();
    initNotifications();
});

// Управление формами
function initForms() {
    const forms = document.querySelectorAll('form');

    forms.forEach(form => {
        form.addEventListener('submit', function(e) {
            if (!validateForm(this)) {
                e.preventDefault();
            }
        });

        // Валидация в реальном времени
        const inputs = form.querySelectorAll('input[required]');
        inputs.forEach(input => {
            input.addEventListener('blur', function() {
                validateField(this);
            });
        });
    });
}

function validateForm(form) {
    let isValid = true;
    const inputs = form.querySelectorAll('input[required]');

    inputs.forEach(input => {
        if (!validateField(input)) {
            isValid = false;
        }
    });

    return isValid;
}

function validateField(input) {
    const value = input.value.trim();
    const errorElement = input.parentElement.querySelector('.error');

    if (!value) {
        showError(input, 'Это поле обязательно для заполнения');
        return false;
    }

    // Проверка email
    if (input.type === 'email') {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        if (!emailRegex.test(value)) {
            showError(input, 'Введите корректный email');
            return false;
        }
    }

    // Проверка пароля
    if (input.type === 'password') {
        if (value.length < 6) {
            showError(input, 'Пароль должен содержать минимум 6 символов');
            return false;
        }
    }

    hideError(input);
    return true;
}

function showError(input, message) {
    const errorElement = input.parentElement.querySelector('.error');
    if (errorElement) {
        errorElement.textContent = message;
        errorElement.style.display = 'block';
    }
    input.classList.add('error-input');
}

function hideError(input) {
    const errorElement = input.parentElement.querySelector('.error');
    if (errorElement) {
        errorElement.style.display = 'none';
    }
    input.classList.remove('error-input');
}

// Управление sidebar
function initSidebar() {
    const sidebarToggles = document.querySelectorAll('.sidebar-toggle');
    const sidebar = document.querySelector('.sidebar');

    if (sidebarToggles.length && sidebar) {
        sidebarToggles.forEach(toggle => {
            toggle.addEventListener('click', function() {
                sidebar.classList.toggle('active');
            });
        });
    }

    // Активный пункт меню
    const currentPath = window.location.pathname;
    const menuLinks = document.querySelectorAll('.sidebar-menu a');

    menuLinks.forEach(link => {
        if (link.getAttribute('href') === currentPath) {
            link.classList.add('active');
        }
    });
}

// Инициализация графиков (если есть Chart.js)
function initCharts() {
    if (typeof Chart === 'undefined') return;

    const chartElements = document.querySelectorAll('.chart-container');

    chartElements.forEach(container => {
        const ctx = container.querySelector('canvas');
        if (!ctx) return;

        const chartType = container.dataset.chartType || 'line';
        const chartData = JSON.parse(container.dataset.chartData || '{}');

        new Chart(ctx.getContext('2d'), {
            type: chartType,
            data: chartData,
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false,
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true
                    }
                }
            }
        });
    });
}

// Уведомления
function initNotifications() {
    // Автоматическое скрытие alert через 5 секунд
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.opacity = '0';
            setTimeout(() => {
                alert.remove();
            }, 300);
        }, 5000);
    });
}

// API функции
const api = {
    async get(url) {
        const response = await fetch(url);
        return await response.json();
    },

    async post(url, data) {
        const response = await fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(data)
        });
        return await response.json();
    },

    async delete(url) {
        const response = await fetch(url, {
            method: 'DELETE'
        });
        return await response;
    }
};

// Утилиты для даты и валюты
const utils = {
    formatDate(dateString) {
        const date = new Date(dateString);
        return date.toLocaleDateString('ru-RU', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    },

    formatCurrency(amount) {
        return new Intl.NumberFormat('ru-RU', {
            style: 'currency',
            currency: 'RUB'
        }).format(amount);
    },

    formatNumber(number) {
        return new Intl.NumberFormat('ru-RU').format(number);
    }
};

// Глобальный объект для хранения состояния
window.appState = {
    user: null,
    categories: [],
    transactions: [],

    setUser(user) {
        this.user = user;
        localStorage.setItem('user', JSON.stringify(user));
    },

    loadUser() {
        const user = localStorage.getItem('user');
        if (user) {
            this.user = JSON.parse(user);
        }
        return this.user;
    }
};

// Инициализация состояния при загрузке
window.appState.loadUser();


// Modal Functions
function showAddTransactionModal() {
  document.getElementById('addTransactionModal').style.display = 'flex';
  document.getElementById('date').valueAsDate = new Date();
}

function closeAddTransactionModal() {
  document.getElementById('addTransactionModal').style.display = 'none';
  document.getElementById('addTransactionForm').reset();
}

// Close modal when clicking outside
window.onclick = function(event) {
  const modal = document.getElementById('addTransactionModal');
  if (event.target === modal) {
      closeAddTransactionModal();
  }
}

// Form submission
const addTransactionForm = document.getElementById('addTransactionForm');
if (addTransactionForm) {
  addTransactionForm.addEventListener('submit', function(e) {
    // Форма будет отправлена обычным способом, без preventDefault
    // Это позволит Spring обработать POST запрос
  });
}

// Mobile menu toggle
function toggleMobileMenu() {
  document.querySelector('.sidebar').classList.toggle('active');
}

// Set current date in date input
document.addEventListener('DOMContentLoaded', function() {
  const today = new Date().toISOString().split('T')[0];
  const dateInputs = document.querySelectorAll('input[type="date"]');
  dateInputs.forEach(input => {
      if (!input.value) {
          input.value = today;
      }
  });

  // Add mobile menu button if needed
  if (window.innerWidth <= 768) {
      const mobileBtn = document.createElement('button');
      mobileBtn.className = 'mobile-menu-btn';
      mobileBtn.innerHTML = '<i class="fas fa-bars"></i>';
      mobileBtn.onclick = toggleMobileMenu;
      document.body.appendChild(mobileBtn);
  }
});

// Auto-refresh every 30 seconds for live data
setInterval(() => {
  // Здесь можно добавить обновление данных
  console.log('Auto-refresh...');
}, 30000);
