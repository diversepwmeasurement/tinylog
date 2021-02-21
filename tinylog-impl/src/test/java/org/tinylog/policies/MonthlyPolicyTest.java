/*
 * Copyright 2021 Martin Winandy
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.tinylog.policies;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.tinylog.configuration.ServiceLoader;
import org.tinylog.util.FileSystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Tests for {@link MonthlyPolicy}.
 */
@RunWith(Enclosed.class)
public final class MonthlyPolicyTest {

	/**
	 * Converts a local date and time to epoch milliseconds.
	 *
	 * @param date
	 *            Local date
	 * @param time
	 *            Local time
	 * @return Milliseconds since 1970-01-01T00:00:00Z
	 */
	private static long asEpochMilliseconds(final LocalDate date, final LocalTime time) {
		return ZonedDateTime.of(date, time, ZoneId.systemDefault()).toInstant().toEpochMilli();
	}

	/**
	 * Tests for daily policy with default time (00:00).
	 */
	@RunWith(PowerMockRunner.class)
	@PrepareForTest(MonthlyPolicy.class)
	public static final class DefaultTimeTest {

		/**
		 * Initialize mocking of {@link System} and {@link Calendar}.
		 */
		@Before
		public void init() {
			mockStatic(System.class, Calendar.class);
		}

		/**
		 * Verifies that an already existing file with the current time as last modification date will be continued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileWithCurrentTime() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(System.currentTimeMillis());

			MonthlyPolicy policy = new MonthlyPolicy(null);
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification of first of current month at midnight will be
		 * continued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileFromStartAtCurrentMonth() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 1), LocalTime.of(0, 0)));

			MonthlyPolicy policy = new MonthlyPolicy(null);
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification at the last month will be discontinued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void discontinueExistingFileFromLasMonth() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 5, 31), LocalTime.of(23, 59)));

			MonthlyPolicy policy = new MonthlyPolicy(null);
			assertThat(policy.continueExistingFile(path)).isFalse();
		}

		/**
		 * Verifies that the current file will be continued immediately after start.
		 */
		@Test
		public void continueCurrentFileAfterStart() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy(null);

			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be still continued one minute before the expected rollover event.
		 */
		@Test
		public void continueCurrentFileOneMinuteBeforeRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy(null);

			setTime(LocalDate.of(1985, 6, 30), LocalTime.of(23, 59));
			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be discontinued at the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileAtRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy(null);

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(0, 0));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Verifies that the current file will be still discontinued one minute after the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileOneMinuteAfterRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy(null);

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(0, 1));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Sets the current date and time.
		 *
		 * @param date
		 *            New current date
		 * @param time
		 *            New current time
		 */
		private static void setTime(final LocalDate date, final LocalTime time) {
			long milliseconds = asEpochMilliseconds(date, time);

			when(System.currentTimeMillis()).thenReturn(milliseconds);
			when(Calendar.getInstance()).then(new CalendarAnswer(milliseconds));
		}

	}

	/**
	 * Tests for daily policy with custom time that contains only an hour (6 a.m.).
	 */
	@RunWith(PowerMockRunner.class)
	@PrepareForTest(MonthlyPolicy.class)
	public static final class CustomHourOnlyTimeTest {

		/**
		 * Initialize mocking of {@link System} and {@link Calendar}.
		 */
		@Before
		public void init() {
			mockStatic(System.class, Calendar.class);
		}

		/**
		 * Verifies that an already existing file with the current time as last modification date will be continued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileWithCurrentTime() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(System.currentTimeMillis());

			MonthlyPolicy policy = new MonthlyPolicy();
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification from last rollover at 6 a.m. on the first day
		 * of the same month will be continued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileFromSameMonth() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 1), LocalTime.of(6, 0)));

			MonthlyPolicy policy = new MonthlyPolicy("6");
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification from last rollover at 5.59 a.m. on the first
		 * day of the same month will be continued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void discontinueExistingFileFromSameMonth() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 1), LocalTime.of(5, 59)));

			MonthlyPolicy policy = new MonthlyPolicy("6");
			assertThat(policy.continueExistingFile(path)).isFalse();
		}

		/**
		 * Verifies that the current file will be continued immediately after start.
		 */
		@Test
		public void continueCurrentFileAfterStart() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("6");

			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be still continued one minute before the expected rollover event.
		 */
		@Test
		public void continueCurrentFileOneMinuteBeforeRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("6");

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(5, 59));
			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be discontinued at the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileAtRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("6");

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(6, 0));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Verifies that the current file will be still discontinued one minute after the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileOneMinuteAfterRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("6");

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(6, 1));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Sets the current date and time.
		 *
		 * @param date
		 *            New current date
		 * @param time
		 *            New current time
		 */
		private static void setTime(final LocalDate date, final LocalTime time) {
			long milliseconds = asEpochMilliseconds(date, time);

			when(System.currentTimeMillis()).thenReturn(milliseconds);
			when(Calendar.getInstance()).then(new CalendarAnswer(milliseconds));
		}

	}

	/**
	 * Tests for daily policy with custom time that contains an hour and minutes (01:30).
	 */
	@RunWith(PowerMockRunner.class)
	@PrepareForTest(MonthlyPolicy.class)
	public static final class CustomFullTimeTest {

		/**
		 * Initialize mocking of {@link System} and {@link Calendar}.
		 */
		@Before
		public void init() {
			mockStatic(System.class, Calendar.class);
		}

		/**
		 * Verifies that an already existing file with the current time as last modification date will be continued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileWithCurrentTime() throws IOException {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(System.currentTimeMillis());

			MonthlyPolicy policy = new MonthlyPolicy("01:30");
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification from last rollover at 01:30 at the same first
		 * day of the current month will be continued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void continueExistingFileFromSameMonth() throws IOException {
			setTime(LocalDate.of(1985, 6, 1), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 1), LocalTime.of(1, 30)));

			MonthlyPolicy policy = new MonthlyPolicy("01:30");
			assertThat(policy.continueExistingFile(path)).isTrue();
		}

		/**
		 * Verifies that an already existing file with last modification at 01:29 at the same first day of the current
		 * month will be discontinued.
		 *
		 * @throws IOException
		 *             Failed creating temporary file
		 */
		@Test
		public void discontinueExistingFileFromSameMonth() throws IOException {
			setTime(LocalDate.of(1985, 6, 1), LocalTime.of(12, 0));

			String path = FileSystem.createTemporaryFile();
			new File(path).setLastModified(asEpochMilliseconds(LocalDate.of(1985, 6, 1), LocalTime.of(1, 29)));

			MonthlyPolicy policy = new MonthlyPolicy("01:30");
			assertThat(policy.continueExistingFile(path)).isFalse();
		}

		/**
		 * Verifies that the current file will be continued immediately after start.
		 */
		@Test
		public void continueCurrentFileAfterStart() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("01:30");

			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be still continued one minute before the expected rollover event.
		 */
		@Test
		public void continueCurrentFileOneMinuteBeforeRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("01:30");

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(1, 29));
			assertThat(policy.continueCurrentFile(null)).isTrue();
		}

		/**
		 * Verifies that the current file will be discontinued at the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileAtRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("01:30");

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(1, 30));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Verifies that the current file will be still discontinued one minute after the expected rollover event.
		 */
		@Test
		public void discontinueCurrentFileOneMinuteAfterRolloverEvent() {
			setTime(LocalDate.of(1985, 6, 3), LocalTime.of(12, 0));
			MonthlyPolicy policy = new MonthlyPolicy("01:30");

			setTime(LocalDate.of(1985, 7, 1), LocalTime.of(1, 31));
			assertThat(policy.continueCurrentFile(null)).isFalse();
		}

		/**
		 * Sets the current date and time.
		 *
		 * @param date
		 *            New current date
		 * @param time
		 *            New current time
		 */
		private static void setTime(final LocalDate date, final LocalTime time) {
			long milliseconds = asEpochMilliseconds(date, time);

			when(System.currentTimeMillis()).thenReturn(milliseconds);
			when(Calendar.getInstance()).then(new CalendarAnswer(milliseconds));
		}

	}

	/**
	 * Tests for daily policy with invalid custom times.
	 */
	public static final class InvalidCustomTimeTest {

		/**
		 * Verifies that an illegal argument exception will be thrown if the time argument does not contain any digits.
		 */
		@Test
		public void nonNumericString() {
			assertThatThrownBy(() -> new MonthlyPolicy("abc")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("abc");
		}

		/**
		 * Verifies that an illegal argument exception will be thrown if the time argument does not contain any digits
		 * after the delimiter.
		 */
		@Test
		public void delimiterWithoutMinutes() {
			assertThatThrownBy(() -> new MonthlyPolicy("01:")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("01:");
		}

	}

	/**
	 * Tests for service registration.
	 */
	public static final class ServiceRegistrationTest {

		/**
		 * Verifies that policy is registered as service under the name "daily".
		 */
		@Test
		public void isRegistered() {
			Policy policy = new ServiceLoader<>(Policy.class, String.class).create("monthly", (String) null);
			assertThat(policy).isInstanceOf(MonthlyPolicy.class);
		}

	}

	/**
	 * Answer for mocked calendars.
	 */
	private static final class CalendarAnswer implements Answer<Calendar> {

		private final long milliseconds;

		/**
		 * @param milliseconds
		 *            Milliseconds since 1970-01-01T00:00:00Z
		 */
		private CalendarAnswer(final long milliseconds) {
			this.milliseconds = milliseconds;
		}

		@Override
		public Calendar answer(final InvocationOnMock invocation) throws Throwable {
			Calendar calendar = Calendar.getInstance();
			calendar.setTimeInMillis(milliseconds);
			return calendar;
		}

	}

}
