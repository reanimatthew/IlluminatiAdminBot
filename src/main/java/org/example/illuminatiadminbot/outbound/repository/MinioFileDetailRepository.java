package org.example.illuminatiadminbot.outbound.repository;

import org.example.illuminatiadminbot.outbound.model.MinioFileDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MinioFileDetailRepository extends JpaRepository<MinioFileDetail, Long> {
    Optional<MinioFileDetail> findByPath(String path);
}
