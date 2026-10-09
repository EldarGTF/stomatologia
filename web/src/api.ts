// Публичный API сервера (/api/public/**). Даты и время приходят без часового пояса — это местное время клиники.

export interface ClinicInfo {
  name: string;
  address: string;
  phone: string;
  email: string | null;
  bookingHorizonDays: number;
  minLeadHours: number;
  patientCancelHours: number;
  lastBookableDay: string;
}

export interface ServiceInfo {
  id: number;
  name: string;
  description: string | null;
  price: number;
  durationMinutes: number;
}

export interface DoctorInfo {
  id: number;
  fullName: string;
  specialtyName: string;
  roomNumber: string;
}

export interface HolidayInfo {
  day: string;
  name: string;
}

export interface Slot {
  doctorId: number;
  doctorName: string;
  roomNumber: string;
  start: string;
  end: string;
}

export interface DayAvailability {
  date: string;
  freeSlots: number;
}

export interface BookingRequest {
  serviceId: number;
  doctorId: number | null;
  startAt: string;
  lastName: string;
  firstName: string;
  phone: string;
  comment: string | null;
  consent: boolean;
  website: string;
}

export type AppointmentStatus = 'SCHEDULED' | 'COMPLETED' | 'NO_SHOW' | 'CANCELLED';

export interface BookingInfo {
  token: string;
  status: AppointmentStatus;
  statusTitle: string;
  startAt: string;
  endAt: string;
  doctorName: string;
  specialtyName: string;
  roomNumber: string;
  serviceName: string;
  price: number;
  patientName: string;
  cancellable: boolean;
  cancelDeadline: string;
  confirmed: boolean;
}

export type ChatRole = 'USER' | 'ASSISTANT' | 'OPERATOR';

export interface ChatMessage {
  id: number;
  role: ChatRole;
  text: string;
  sentAt: string;
}

/** operator — разговор ведёт администратор клиники, ответ придёт не сразу. */
export interface ChatState {
  sessionId: string;
  messages: ChatMessage[];
  operator: boolean;
}

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

const BASE = '/api/public';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(BASE + path, {
      ...init,
      headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}) },
    });
  } catch {
    throw new ApiError(0, 'Нет связи с сервером. Проверьте интернет и попробуйте ещё раз');
  }
  if (!response.ok) {
    let message = 'Что-то пошло не так. Попробуйте ещё раз или позвоните в клинику';
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      // ответ без JSON — остаётся общее сообщение
    }
    throw new ApiError(response.status, message);
  }
  return response.json() as Promise<T>;
}

const query = (params: Record<string, string | number | null | undefined>) =>
  '?' +
  Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${k}=${encodeURIComponent(String(v))}`)
    .join('&');

export const api = {
  clinic: () => request<ClinicInfo>('/clinic'),
  services: () => request<ServiceInfo[]>('/services'),
  doctors: () => request<DoctorInfo[]>('/doctors'),
  holidays: (from: string, to: string) => request<HolidayInfo[]>('/holidays' + query({ from, to })),
  days: (serviceId: number, doctorId: number | null, from: string, to: string) =>
    request<DayAvailability[]>('/slots/days' + query({ serviceId, doctorId, from, to })),
  slots: (serviceId: number, doctorId: number | null, date: string) =>
    request<Slot[]>('/slots' + query({ serviceId, doctorId, date })),
  nearest: (serviceId: number, doctorId: number | null) =>
    request<Slot>('/slots/nearest' + query({ serviceId, doctorId })),
  book: (body: BookingRequest) => request<BookingInfo>('/bookings', { method: 'POST', body: JSON.stringify(body) }),
  booking: (token: string) => request<BookingInfo>(`/bookings/${encodeURIComponent(token)}`),
  cancel: (token: string) =>
    request<BookingInfo>(`/bookings/${encodeURIComponent(token)}/cancel`, { method: 'POST' }),
  ticketUrl: (token: string) => `${BASE}/bookings/${encodeURIComponent(token)}/ticket`,
  chatSend: (sessionId: string | null, text: string, website: string) =>
    request<ChatState>('/chat/messages', { method: 'POST', body: JSON.stringify({ sessionId, text, website }) }),
  chatPoll: (sessionId: string, after: number) =>
    request<ChatState>(`/chat/${encodeURIComponent(sessionId)}` + query({ after })),
};
