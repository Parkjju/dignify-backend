package com.rta.dignify.service;

import com.rta.dignify.domain.Pick;
import com.rta.dignify.domain.Report;
import com.rta.dignify.domain.ReportReason;
import com.rta.dignify.domain.User;
import com.rta.dignify.dto.report.ReportCreate;
import com.rta.dignify.global.exception.BusinessException;
import com.rta.dignify.global.exception.ErrorCode;
import com.rta.dignify.repository.PickRepository;
import com.rta.dignify.repository.ReportRepository;
import com.rta.dignify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class ReportService {
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PickRepository pickRepository;

    @Transactional
    public void create(Long userId, ReportCreate request) {
        Pick pick = pickRepository.findById(request.pickId()).orElseThrow(() -> new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST));
        if (pick.getIsDeleted()) {
            throw new BusinessException(ErrorCode.PICK_DOES_NOT_EXIST);
        }

        if (reportRepository.existsByPickIdAndUserId(pick.getId(), userId)) {
            return;
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String detail = request.reason() == ReportReason.OTHER ? request.detail() : null;
        reportRepository.save(Report.create(pick, user, request.reason(), detail));
    }
}
