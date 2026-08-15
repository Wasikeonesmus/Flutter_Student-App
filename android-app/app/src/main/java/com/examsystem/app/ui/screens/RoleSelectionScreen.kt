package com.examsystem.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.examsystem.app.ui.theme.*

@Composable
fun RoleSelectionScreen(
    onInstructorSelected: () -> Unit,
    onStudentSelected: () -> Unit,
    onTestResultsSelected: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(RedPrimary, Color.Black)
                )
            )
    ) {
        // Decorative Circles
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color.Red.copy(alpha = 0.2f), radius = 300f, center = androidx.compose.ui.geometry.Offset(0f, 0f))
            drawCircle(color = Color.Red.copy(alpha = 0.15f), radius = 250f, center = androidx.compose.ui.geometry.Offset(size.width, 400f))
            drawCircle(color = Color.Red.copy(alpha = 0.1f), radius = 400f, center = androidx.compose.ui.geometry.Offset(200f, size.height))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(25.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("E", color = Color.White, fontSize = 50.sp, fontWeight = FontWeight.ExtraBold)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "ExamPro",
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Premium Examination Platform",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Selection Text
            Text(
                text = "Welcome Back",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Instructor Button (Outline Style)
            OutlinedButton(
                onClick = onInstructorSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(text = "INSTRUCTOR", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Student Button (Solid White Style)
            Button(
                onClick = onStudentSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text(text = "STUDENT", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test Results Button (Outlined Style)
            OutlinedButton(
                onClick = onTestResultsSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text(text = "TEST RESULTS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
            
            Text(
                text = "Powered by SWF",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}
