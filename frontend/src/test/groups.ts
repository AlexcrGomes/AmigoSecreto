import { http, HttpResponse } from 'msw';
import type { GroupDetail } from '../api/groups';
import { ANA } from './render';
import { server } from './server';

export const BRUNO_ID = '22222222-2222-2222-2222-222222222222';

export function openGroup(overrides: Partial<GroupDetail> = {}): GroupDetail {
  return {
    id: 'g-1',
    name: 'Natal 2026',
    code: 'K7PQ-M2XA',
    status: 'OPEN',
    drawnAt: null,
    participants: [
      { userId: ANA.id, name: 'Ana', organizer: true, confirmed: true },
      { userId: BRUNO_ID, name: 'Bruno', organizer: false, confirmed: true },
    ],
    participantCount: 2,
    maxParticipants: 100,
    organizer: true,
    canDraw: false,
    eventDetails: {
      description: null,
      eventDate: '2026-12-19',
      eventTime: '19:00',
      location: 'Casa da Ana',
      giftValue: null,
    },
    eventDetailsMissing: false,
    eventDatePassed: false,
    detailsVersion: 1,
    confirmedCount: 2,
    myConfirmation: 'CONFIRMED',
    drawBlockedReason: 'not-enough-participants',
    finishedAt: null,
    drawUndone: false,
    myExitRequest: null,
    pendingExitRequests: [],
    myWishlist: { items: [], maxItems: 5, editable: true },
    myDependents: [],
    maxDependents: 5,
    canAddDependents: true,
    dependentsBlockedReason: null,
    ...overrides,
  };
}

export function problem(status: number, detail: string, type?: string) {
  return HttpResponse.json(
    { status, detail, type },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  );
}

/** GET /groups/:id responde com o grupo (ou com a sequência de grupos, um por chamada). */
export function mockGroup(...versions: GroupDetail[]) {
  let call = 0;
  server.use(
    http.get('/api/v1/groups/:id', () => {
      const group = versions[Math.min(call, versions.length - 1)];
      call += 1;
      return HttpResponse.json(group);
    }),
  );
}
