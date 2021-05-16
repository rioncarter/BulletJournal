package com.bulletjournal.controller;

import com.bulletjournal.controller.models.BookingLink;
import com.bulletjournal.controller.models.BookingSlot;
import com.bulletjournal.controller.models.RequestParams;
import com.bulletjournal.controller.models.params.CreateBookingLinkParams;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class BookingLinkControllerTest {
    private static final String USER = "BulletJournal";

    private static final String ROOT_URL = "http://localhost:";

    private static String TIMEZONE = "America/Los_Angeles";

    @LocalServerPort
    int randomServerPort;
    private TestRestTemplate restTemplate = new TestRestTemplate();
    private RequestParams requestParams;

    @Before
    public void setup() {
        restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
        requestParams = new RequestParams(restTemplate, randomServerPort);
    }

    @Test
    public void testCRUD() throws Exception {
        BookingLink bookingLink1 = createBookingLink("2021-05-04", "2021-06-01", TIMEZONE, 30, 0, false, true);
        BookingLink bookingLink2 = createBookingLink("2021-06-01", "2021-06-04", TIMEZONE, 60, 0, false, true);
        bookingLink1 = getBookingLink(bookingLink1.getId(), TIMEZONE);
        bookingLink2 = getBookingLink(bookingLink2.getId(), TIMEZONE);

        BookingSlot bookingSlot = new BookingSlot();
        bookingSlot.setIndex(0);
        bookingSlot.setStartTime("00:00");
        bookingSlot.setEndTime("00:30");
        bookingSlot.setDate("2021-05-04");
        bookingSlot.setOn(true);

        updateBookingLinkSlot(bookingLink1.getId(), bookingSlot);

        deleteBookingLinkSlot(bookingLink2.getId());
    }

    private BookingLink createBookingLink(String startDate, String endDate, String timezone, int slotSpan,
                                          int bufferInMin, boolean includeTaskWithoutDuration, boolean expireOnBooking) {
        CreateBookingLinkParams createBookingLinkParams = new CreateBookingLinkParams(startDate, endDate, includeTaskWithoutDuration, expireOnBooking);
        createBookingLinkParams.setBufferInMin(bufferInMin);
        createBookingLinkParams.setSlotSpan(slotSpan);
        createBookingLinkParams.setTimezone(timezone);

        ResponseEntity<BookingLink> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + BookingLinksController.BOOKING_LINKS_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(createBookingLinkParams),
                BookingLink.class);
        BookingLink bookingLink = response.getBody();
        assertNotNull(bookingLink);
        return bookingLink;
    }

    private BookingLink getBookingLink(String bookingLinkId, String timeZone) {
        String url = ROOT_URL + randomServerPort + BookingLinksController.PUBLIC_BOOKING_LINK_ROUTE;
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(url).queryParam("timeZone", timeZone);
        ResponseEntity<BookingLink> response = this.restTemplate.exchange(
                uriBuilder.toUriString(),
                HttpMethod.GET,
                null,
                BookingLink.class,
                bookingLinkId);
        BookingLink bookingLink = response.getBody();
        assertNotNull(bookingLink);
        return bookingLink;
    }

    private BookingLink updateBookingLinkSlot(String bookingLinkId, BookingSlot bookingSlot) {
        ResponseEntity<BookingLink> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + BookingLinksController.BOOKING_LINK_UPDATE_SLOT_ROUTE,
                HttpMethod.POST,
                new HttpEntity<>(bookingSlot),
                BookingLink.class,
                bookingLinkId);
        BookingLink bookingLink = response.getBody();
        assertNotNull(bookingLink);
        assertEquals(bookingLink.getSlots().get(bookingSlot.getIndex()).isOn(), bookingSlot.isOn());
        return bookingLink;
    }

    private void deleteBookingLinkSlot(String bookingLinkId) {
        ResponseEntity<?> response = this.restTemplate.exchange(
                ROOT_URL + randomServerPort + BookingLinksController.PUBLIC_BOOKING_LINK_ROUTE,
                HttpMethod.DELETE,
                null,
                Void.class,
                bookingLinkId);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        BookingLink bookingLink = getBookingLink(bookingLinkId, TIMEZONE);
        assertNull(bookingLink);
    }
}
