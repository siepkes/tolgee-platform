/**
 * Copyright (C) 2023 Tolgee s.r.o. and contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.tolgee.notifications

import io.tolgee.repository.NotificationsRepository
import io.tolgee.testing.AuthorizedControllerTest
import io.tolgee.testing.assert
import io.tolgee.util.addMilliseconds
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.scheduling.TaskScheduler
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

abstract class AbstractNotificationTest : AuthorizedControllerTest() {
  @Autowired
  lateinit var notificationService: NotificationService

  @Autowired
  lateinit var taskScheduler: TaskScheduler

  @SpyBean
  @Autowired
  lateinit var notificationsRepository: NotificationsRepository

  lateinit var semaphore: Semaphore

  @BeforeEach
  fun setupWatcher() {
    semaphore = Semaphore(0)

    doAnswer {
      entityManager.persist(it.arguments[0])
      entityManager.flush()

      entityManager.refresh(it.arguments[0])

      // Wait a bit to make sure everything's *actually* persisted
      // Kind of an ugly way to synchronize everything, but it is what it is
      taskScheduler.schedule(
        { semaphore.release() },
        Date().addMilliseconds(100)
      )

      it.arguments[0]
    }.`when`(notificationsRepository).save(any())
  }

  @AfterEach
  fun clearWatcher() {
    Mockito.reset(notificationsRepository)
  }

  fun waitUntilNotificationDispatch(count: Int = 1) {
    val dispatched = semaphore.tryAcquire(count, 2L, TimeUnit.SECONDS)
    dispatched.assert
      .withFailMessage("Expected at least $count notification(s) to be dispatched.")
      .isTrue()
  }

  fun ensureNoNotificationDispatch() {
    val dispatched = semaphore.tryAcquire(2L, TimeUnit.SECONDS)
    dispatched.assert
      .withFailMessage("Expected no notifications to be dispatched.")
      .isFalse()
  }
}
