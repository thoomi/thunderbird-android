package net.thunderbird.feature.applock.impl.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test

class AppLockTimeoutCalculatorTest {

    @Test
    fun `isTimeoutExceeded should return false when never hidden`() {
        // Arrange
        val testSubject = AppLockTimeoutCalculator()

        // Act
        val result = testSubject.isTimeoutExceeded(
            lastUiHiddenMillis = 0L,
            timeoutMillis = 60_000L,
        )

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `isTimeoutExceeded should return true when timeout has elapsed`() {
        // Arrange
        val currentTime = 100_000L
        val testSubject = AppLockTimeoutCalculator(clock = { currentTime })

        // Act - UI hidden 2 minutes ago, timeout is 1 minute
        val result = testSubject.isTimeoutExceeded(
            lastUiHiddenMillis = currentTime - 120_000L,
            timeoutMillis = 60_000L,
        )

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `isTimeoutExceeded should return false when timeout has not elapsed`() {
        // Arrange
        val currentTime = 100_000L
        val testSubject = AppLockTimeoutCalculator(clock = { currentTime })

        // Act - UI hidden 30 seconds ago, timeout is 1 minute
        val result = testSubject.isTimeoutExceeded(
            lastUiHiddenMillis = currentTime - 30_000L,
            timeoutMillis = 60_000L,
        )

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `isTimeoutExceeded should return true when exactly at timeout`() {
        // Arrange
        val currentTime = 100_000L
        val testSubject = AppLockTimeoutCalculator(clock = { currentTime })

        // Act - UI hidden exactly 1 minute ago, timeout is 1 minute
        val result = testSubject.isTimeoutExceeded(
            lastUiHiddenMillis = currentTime - 60_000L,
            timeoutMillis = 60_000L,
        )

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `getRemainingTimeMillis should return 0 when never hidden`() {
        // Arrange
        val testSubject = AppLockTimeoutCalculator()

        // Act
        val result = testSubject.getRemainingTimeMillis(
            lastUiHiddenMillis = 0L,
            timeoutMillis = 60_000L,
        )

        // Assert
        assertThat(result).isEqualTo(0L)
    }

    @Test
    fun `getRemainingTimeMillis should return remaining time when not timed out`() {
        // Arrange
        val currentTime = 100_000L
        val testSubject = AppLockTimeoutCalculator(clock = { currentTime })

        // Act - UI hidden 30 seconds ago, timeout is 1 minute
        val result = testSubject.getRemainingTimeMillis(
            lastUiHiddenMillis = currentTime - 30_000L,
            timeoutMillis = 60_000L,
        )

        // Assert
        assertThat(result).isEqualTo(30_000L)
    }

    @Test
    fun `getRemainingTimeMillis should return 0 when timeout exceeded`() {
        // Arrange
        val currentTime = 100_000L
        val testSubject = AppLockTimeoutCalculator(clock = { currentTime })

        // Act - UI hidden 2 minutes ago, timeout is 1 minute
        val result = testSubject.getRemainingTimeMillis(
            lastUiHiddenMillis = currentTime - 120_000L,
            timeoutMillis = 60_000L,
        )

        // Assert
        assertThat(result).isEqualTo(0L)
    }
}
