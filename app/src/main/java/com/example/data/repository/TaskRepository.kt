package com.example.data.repository

import com.example.data.db.TaskDao
import com.example.data.model.RoutineTask
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val taskDao: TaskDao) {
    val allTasks: Flow<List<RoutineTask>> = taskDao.getAllTasks()

    suspend fun insert(task: RoutineTask): Long = taskDao.insertTask(task)

    suspend fun update(task: RoutineTask) = taskDao.updateTask(task)

    suspend fun delete(task: RoutineTask) = taskDao.deleteTask(task)

    suspend fun deleteById(id: Int) = taskDao.deleteTaskById(id)

    suspend fun ensureDefaultTasks() {
        // App starts with no tasks by default
    }
}
